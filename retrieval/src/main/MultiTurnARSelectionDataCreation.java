package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.FileUtil;

/**The script for creating the train/valid/test data of MSDialog data used in the following SIGIR'18 full paper.
 * Liu Yang, Minghui Qiu, Chen Qu, Jiafeng Guo, Yongfeng Zhang, W. Bruce Croft, Jun Huang and Haiqing Chen
 * Response Ranking with Deep Matching Networks and External Knowledge in Information-seeking Conversation Systems, SIGIR'18.
 * 
 * Adopt the standard task setting for multi-turn answer response selection using benchmark data including Ubuntu, Douban,
 * MS dialog as in Yu Wu et al. in ACL'17 and Rui Yan et al. in SIGIR'17/SIGIR'16/CIKM'16.
 * Create data format like <label, context_query, reply> triples using the Microsoft Customer Service Dialog Data
 * 
 * 1. Select dialogs with <= 100 turns (set maxNunTurns = 100) (Filter too long dialogs) in train/valid/test
 * The context size c = min(9, pre_turn_num). Generate previous context for each question/user input by
 *  merging previous c turns + 1st question	(at most 9 previous utterances + the 1st question). The 1st question
 * is very important for response finding.
 * 2. Use negative sampling to create negative answer candidates referring to the MV-LSTM paper (Shengxian Wan et al.)
 * in AAAI'16 (http://www.bigdatalab.ac.cn/~gjf/papers/2016/AAAI2016_wan.pdf). For each input/question except 1st
 * question (no context for 1st question) by users, use its ground truth answer as the query to retrieve the top 
 * 1,000 results from the whole answer set by Agents(except the answer set in the testing data, since we assume that
 * the testing data is not available during model developments) with BM25 in Lucene. Then we randomly select 9 answers
 * from them to construct the negative candidate answers. Note that at this point, we already separate the whole dataset
 * to the training, validation and testing data with proportion 8:1:1 by the posting time of utterances. After these data
 *  filtering/processing steps, these train/valid/test ratio could be changed.				
 * 3. Rerank these 10 candidate answer responses with DMN/DMN-PRF/DMN-KD model and other deep matching models like ARC-I,
 *  ARC-II, DSSM/CDSSM, MatchPyramid, DRMM/aNMM, etc with implementations using the MatchZoo framework. (Try to add 
 *  more implemented models into MatchZoo)				
 * 4. Retrieve relevant QA pairs in SO dump for candidate response expansion to add PRF into DMN model.
 * 5. Deeper analysis on performances under different context lengths （3 turns, 5 turns, 7 turns, …, etc.)
 * 
 * @author "Liu Yang" <lyang@cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @homepage https://sites.google.com/site/lyangwww/
*/

public class MultiTurnARSelectionDataCreation {

	public static long indexedQAPostNum = 0;
	
	public static void main(String[] args) throws IOException, ParseException {
		String basedModelInputDataPath = "data/MicrosoftCSQA/v1-2/";
		String multiTurnARSelectionDataPath = "data/MicrosoftCSQA/v1-2MultiTurnARSelectionRerun04152018/";
		String AgentAnswerResponsesIndexPath = basedModelInputDataPath + "AgentAnswerResponsesIndex/";
		String dialogContentFile = basedModelInputDataPath + "dialog_contents.tsv";
		String dialogTitleFile = basedModelInputDataPath + "dialog_titles.tsv";
		
		//1. Build index for Microsoft customer service dialog data using the whole answer responses set 
		// by Agents(before filtering, but except the answer set in the testing data, since we assume that
		// the future testing data is not available during model developments) with Lucene.
		//buildIndexAgentAnswerResponsesExceptTestData(basedModelInputDataPath, multiTurnARSelectionDataPath,
		//		AgentAnswerResponsesIndexPath);
		
		//2. Filter long dialogs and generate (Context, Question/UserInput, Reply) triples
		// When generate context, use the raw QA post text in dialog_contents.tsv
		Map<String, String> idToRawQAPostTextMap = new HashMap<String, String>();
		Map<String, String> dialogIDToDialogTitleMap = new HashMap<String, String>();
		initIDToRawQAPostTextMap(idToRawQAPostTextMap, dialogContentFile);
		initDialogIDToDialogTitleMap(dialogIDToDialogTitleMap, dialogTitleFile);
		
		for(String dataPartition : new String[]{"train", "valid", "test"}){
			generateDialogContext(basedModelInputDataPath, dataPartition, idToRawQAPostTextMap,
					dialogIDToDialogTitleMap, multiTurnARSelectionDataPath);
		}
		
		//3. For each (Context, Question/UserInput, Reply) triple, use negative sampling to generate
		// Negative answer/reply candidates
		Analyzer analyzer = new StandardAnalyzer();
		int hitsPerPage = 1000;
		Directory indexDir = FSDirectory.open(Paths.get(AgentAnswerResponsesIndexPath));
	    IndexReader indexReader = DirectoryReader.open(indexDir);
	    IndexSearcher searcher = new IndexSearcher(indexReader);
	    searcher.setSimilarity(new BM25Similarity());
		//Note that the question key is also the query question
	    //For each input/question except 1st question (no context for 1st question) by users, use its 
	    //ground truth answer as the query to retrieve the top 1,000 results
		//Then we randomly select 9 answers from them to construct the negative candidate answers. 
	    for(String dataPartition : new String[]{"train", "valid", "test"}){
	    		String cqrFile = multiTurnARSelectionDataPath + dataPartition + "_context_query_reply.tsv";
		    String negInstanceFile = multiTurnARSelectionDataPath + dataPartition + "_neg_reply_instances.tsv";
		    System.out.println("generate file: " + negInstanceFile);
			ArrayList<String> queryLines = new ArrayList<String>();
			FileWriter negWriter = new FileWriter(negInstanceFile);
			FileUtil.readLines(cqrFile, queryLines);
			// The format of the negative candidate answer file
			// DID-1-UTID-3 \t negative candidate answers
			int qNum = 0;
			for(String queryLine : queryLines){
				qNum++;
				if(qNum % 100 == 0){
					System.out.println("qNum: " + qNum);
				}
				//add neg candidates
				//set sampleNegCandidateNumPerQuery as 9
				//For each context_question, we have 9 negative responses + 1 positive response
				sampleNegativeCandidate(negWriter, queryLine, searcher, analyzer, 9, hitsPerPage);
				negWriter.flush();
			}
			negWriter.close();
	    }
		indexReader.close();
	}
	
	private static void sampleNegativeCandidate(FileWriter mergeWriter, String queryLine,
			IndexSearcher searcher, Analyzer analyzer, int sampleNegCandidateNumPerQuery, int hitsPerPage) throws ParseException, IOException {
		// TODO Auto-generated method stub
		// The format of the negative candidate answer response file
		// DID-1-UTID-3 \t negative candidate answers
		int minLen = 3;
		int maxLen = 400;
		String [] tokens = queryLine.split("\t");
		String qid = tokens[0];
		String query = tokens[3]; // use the ground truth positive answer as the query 
		ArrayList<String> qWords = new ArrayList<>();
		FileUtil.tokenizeStanfordTKAndLowerCase(query, qWords, false); //tokenization, remove punctuations, lower case, remove stop words
		String qString = "";
		for(String qw : qWords){
			qString += " " + qw;
		}
		if(qString.trim().equals("") || qString.trim() == null) return; //If qString is null (question words are all stop words), continue
		BooleanQuery.setMaxClauseCount(102400);
		Query q = new QueryParser("UttText", analyzer).parse(QueryParser.escape(qString));
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
		//System.out.println("query: " + query);
		//System.out.println("collector: " + collector);
		searcher.search(q, collector);
	    ArrayList<ScoreDoc> hitsList = new ArrayList<ScoreDoc>();
	    for(ScoreDoc s : collector.topDocs().scoreDocs){
	    	hitsList.add(s);
	    }
	    //System.out.println("Query: " + query + " , Found " + hitsList.size() + " hits");
	    int curSampleNum = 1;
	    Set<String> sampledNegCandidateSet = new HashSet<String>();//use a set to avoid sample the same candidate twice!
	    int maxSampleTime = 10000; //if can't get 9 neg candidate after 10000 times of sampling, break
	    int sampleTime = 0;
	    while(curSampleNum <= sampleNegCandidateNumPerQuery && sampleTime < maxSampleTime){
	    	sampleTime++;
	    	int pick = (int)(Math.random() * hitsList.size());
	    	int docId = hitsList.get(pick).doc;
		    Document d = searcher.doc(docId);
	    	if(d.get("UttText").equals(query)) {
	    		//System.out.println("found positive question: " + d.get("questionInCluster"));
	    		continue; //positive, skip!
	    	}
	    	String sampledNegQ = d.get("UttText");//.replaceAll("\\pP|\\pS", "");
	    	int qLen = sampledNegQ.split(" ").length;
	    	if(qLen < minLen || qLen > maxLen) continue;
	    	if(sampledNegCandidateSet.contains(sampledNegQ)) continue;
	    	String negQID = qid + "-NegUtt-" + curSampleNum;
	    	mergeWriter.write(qid + "\t" + negQID + "\t" + sampledNegQ + "\n");
	    	curSampleNum++;
	    	sampledNegCandidateSet.add(sampledNegQ);
	    	hitsList.remove(pick);//remove this one since it is already picked
	    }			
	}
	
	//Build index for Microsoft customer service dialog data using the whole answer responses set by Agents
	private static void buildIndexAgentAnswerResponsesExceptTestData(String basedModelInputDataPath,
			String multiTurnARSelectionDataPath, String indexPath) throws IOException {
		// TODO Auto-generated method stub
		Set<String> testDialogID = new HashSet<>();
		String testFile = multiTurnARSelectionDataPath + "test_context_query_reply.tsv";
		String dialogContentFile = basedModelInputDataPath + "dialog_contents.tsv";
		ArrayList<String> lines = new ArrayList<>();
		initTestDialogIDSet(testDialogID, testFile);
		Analyzer analyzer = new StandardAnalyzer();
		if(!new File(indexPath).exists()){ //If the index path is not existed, created it
	    	System.out.println("create index path: " + indexPath);
	    	new File(indexPath).mkdir();
	    }
	    Directory indexDir = FSDirectory.open(Paths.get(indexPath));
	    System.out.println("Indexing to directory '" + indexPath + "'...");
	    // If there are already files under the indexPath, clear them firstly
	    for(File f : new File(indexPath).listFiles()){
	    	f.delete();
	    	System.out.println("delete file: " + f.getAbsolutePath());
	    }
	    IndexWriterConfig config = new IndexWriterConfig(analyzer);
	    //Set some parameters for faster indexing purpose
	    //Set setMaxBufferedDocs or setRAMBufferSizeMB
	    //config.setMaxBufferedDocs(Integer.MAX_VALUE);
	    config.setRAMBufferSizeMB(1024); //1GB
	    IndexWriter w = new IndexWriter(indexDir, config);
	    FileUtil.readLines(dialogContentFile, lines);
	    for(String line : lines){
	    	String [] tokens = line.split("\t");
	    	if(tokens.length < 9 || testDialogID.contains(tokens[0]) || tokens[2].equals("User") ){
	    		continue;
	    	}
	    	Document curD = new Document();
	    	curD.add(new StringField("DialogID", tokens[0], Field.Store.YES));
            curD.add(new StringField("UtteranceID", tokens[1], Field.Store.YES));
            curD.add(new TextField("UttText", tokens[8], Field.Store.YES));
            indexedQAPostNum++;
            if(indexedQAPostNum % 100000 == 0){
 				System.out.println("cur indexed doc number: " + indexedQAPostNum);
 			 }
        	try {
				w.addDocument(curD);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	    }
	    w.close();
		System.out.println("build index done!");
	}
	
	private static void initTestDialogIDSet(Set<String> testDialogID, String testFile) {
		// TODO Auto-generated method stub
		ArrayList<String> lines = new ArrayList<>();
		FileUtil.readLines(testFile, lines);
		for(String line : lines){
			testDialogID.add(line.split("\t")[0].split("-")[1]);
		}
		System.out.println("number of dialogs in test data: " + testDialogID.size());
	}

	private static void generateDialogContext(String basedModelInputDataPath, String dataPartition, Map<String, String> idToRawQAPostTextMap, Map<String, String> dialogIDToDialogTitleMap, String multiTurnARSelectionDataPath) throws IOException {
		// TODO Auto-generated method stub
		String dialogFile = basedModelInputDataPath + dataPartition + ".tsv";
		String dialogIDMaxTurnsFile = basedModelInputDataPath + "dialog_id_" + dataPartition + "_max100turns.tsv";
		String CQATripleFile = multiTurnARSelectionDataPath + dataPartition + "_context_query_reply.tsv";
		FileWriter CQATripleFileWriter = new FileWriter(CQATripleFile);
		Set<String> dialogIDMaxTurnIDSet = new HashSet<>();
		ArrayList<String> lines = new ArrayList<>();
		FileUtil.readLines(dialogIDMaxTurnsFile, lines);
		for(String id : lines){
			dialogIDMaxTurnIDSet.add(id);
		}
		lines.clear();
		FileUtil.readLines(dialogFile, lines);
		System.out.println("cur dialogFile: " + dialogFile + " " + lines.size());
		String curDialogID = "INIT";
		int cacheLength = 10;
		ArrayList<String> cacheContext = new ArrayList<String>(); //a cache with fixed length
		String firstQ = "";
		for(int i = 0; i < lines.size(); i++){
			String [] tokens = lines.get(i).split("\t");
			if(tokens.length < 10) continue;
			if(!dialogIDMaxTurnIDSet.contains(tokens[1])) continue;
			String curRawPostText = idToRawQAPostTextMap.get(tokens[1] + "-" + tokens[2]);
			//id \t dialog_id  \t utterance_id \t user/agent \t user_id \t vote \t answer_time \t affiliation \t isAnswer \t utterance_content
			if(!tokens[1].equals(curDialogID)){
				//New dialog
				firstQ = "<<<USER>>>: " + curRawPostText;
				cacheContext.clear();
				//Since there is no context for the 1st question, continue
				curDialogID = tokens[1];
			} else{
				//Same dialog
				String curUtt = " <<<" + tokens[3].toUpperCase() + ">>>: " + curRawPostText;
				//update the most recent 10 utterance
				if(cacheContext.size() < cacheLength){//max = 9 (plus firstQ = 10)
					cacheContext.add(curUtt); // Key comments on 04142018: note that the current utterance has been added into cacheContext!
				} else{
					cacheContext.remove(0);
					cacheContext.add(curUtt);
				}
				
				if(tokens[3].toLowerCase().equals("user")){
					//If answer (by agent) to this question (by user) is not existed, continue;
					if(i+1 >= lines.size()) continue;
					String afterLine = lines.get(i+1);
					String [] afterTokens = afterLine.split("\t");
					if(!afterTokens[1].equals(tokens[1]) || !afterTokens[3].toLowerCase().equals("agent")) continue;
					String curQ = "<<<USER>>>: " + curRawPostText;
					String curA = "<<<AGENT>>>: " + idToRawQAPostTextMap.get(afterTokens[1] + "-" + afterTokens[2]);
					String curQID = "DID-" + tokens[1] + "-UTID-" + tokens[2];
					String categoryTitle = dialogIDToDialogTitleMap.get(curDialogID);
					//Key comments on 04142018: note that the current utterance has been added into cacheContext!
					//Thus the last utterance of the context is the same with the input query curQ
					//To avoid the duplicated last utterance in the dialog context, here we should not output curQ anymore in the context
					//Since curQ has been included in context. In this case, the query is the dialog context
					//We can further split the dialog context into multiple utterances by <<<USER>>> and <<<AGENT>>> later
					//Fixed this on 04142018
					//!The context is the 1st question in the dialog + most recent 9 utterances (if has, except the current utterance) 
					String context = firstQ;
					for(int ii = 0; ii < cacheContext.size()-1; ii++){//!add all previous utterances except the current utterance into context
						context += " " + cacheContext.get(ii);
					}
					CQATripleFileWriter.append(curQID + "\t" + context + "\t" + curQ + "\t" + curA + "\t" + categoryTitle + "\n"); 
					CQATripleFileWriter.flush();
				}
			}
		}
		CQATripleFileWriter.close();
	}
	
	public static void initDialogIDToDialogTitleMap(Map<String, String> dialogIDToDialogTitleMap,
			String dialogTitleFile) {
		// TODO Auto-generated method stub
		ArrayList<String> lines = new ArrayList<>();
		FileUtil.readLines(dialogTitleFile, lines);
		for(String l : lines){
			String[] tokens = l.split("\t");
			dialogIDToDialogTitleMap.put(tokens[0], tokens[3] + " " + tokens[4]);// ID -> category+title
		}
	}

	public static void initIDToRawQAPostTextMap(Map<String, String> idToRawQAPostTextMap, String dialogContentFile) throws IOException {
		// TODO Auto-generated method stub
		BufferedReader dcBR = new BufferedReader(new FileReader(dialogContentFile));
		String line = null;
		while((line = dcBR.readLine()) != null){
			String[] tokens = line.split("\t");
			if(tokens.length < 9) continue;
			idToRawQAPostTextMap.put(tokens[0] + "-" + tokens[1], tokens[8]);// DialogID-UttID -> Utterance
		}
		dcBR.close();
	}
}
