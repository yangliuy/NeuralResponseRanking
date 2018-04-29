package main;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
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
import com.ValueComparator;


/**Implementation of response candidate expansion phase in DMN-PRF model
 * 1. Retrieve top K Q&A posts from external CQA collection (SO&AskUbuntu) using the candidate
 *    response r_k as the query
 * 2. Generate the expanded responses with the most frequent (by TF) terms or the most specific
 *    terms (by IDF) extracted from the top K Q&A posts
 * 3. Run deep text matching models on the dialog context and expanded candidate responses
 * 4. For MSDialog, use SOTwoYear as external knowledge source; For Ubuntu, use askUbuntu as external 
 *    knowledge source
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class DMNPRFExpandCandidateResponse {
	
	public static void main(String[] args) throws IOException, ParseException{
		
		//int hitsPerPage = 10;
		//int expandTermNum = 10;
		//String searchFieldInPosts = "bodyText"; // Can search bodyText or titleText of SO posts
		if(args.length < 4){
	    	System.out.println("please input params: hitsPerPage expandTermNum searchFieldInPosts dataName");
	    	System.exit(1);
	    }
		int hitsPerPage = Integer.valueOf(args[0]), expandTermNum = Integer.valueOf(args[1]);
	    String searchFieldInPosts = args[2], dataName = args[3];// the dataName could be ms or udc
	    System.out.println("input params hitsPerPage expandTermNum searchFieldInPosts: " + hitsPerPage + "\t" + expandTermNum + "\t" + searchFieldInPosts + "\t" + dataName);
		String modelInputDataPath = "../../PycharmProjects/NLPIRNNMatchZooQA/src-match-zoo-lyang-dev/data/" + dataName + "/ModelInput/";
		String indexPath = "";
		if(dataName.equals("ms")){
			indexPath = "data/ExternalCollection/stackoverflow/so-index-qandaposts-two-years/";
		} else{
			indexPath = "data/ExternalCollection/askubuntu/askubuntu-index-qandaposts-whole/";
		}

		Analyzer analyzer = new StandardAnalyzer();
		Directory indexDir = FSDirectory.open(Paths.get(indexPath));
	    IndexReader indexReader = DirectoryReader.open(indexDir);
	    IndexSearcher searcher = new IndexSearcher(indexReader);
	    searcher.setSimilarity(new BM25Similarity());
	    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		
		for (String dataPart : new String[]{"train", "valid", "test"}){
			String inputDataFile = modelInputDataPath + dataPart + ".txt.mz";// input file format
			String outDataFile = modelInputDataPath + dataPart + "_resp_expansion_" + searchFieldInPosts + ".txt"; // the output file with candidate responses expanded
			String irResultsFile = modelInputDataPath + dataPart + "_dmnprf_ir_res_" + searchFieldInPosts + ".txt"; // the file storing the query and retrieval results in the IR module of dmn-prf
			System.out.println("generate file : " + outDataFile + " , ir results stored in " + irResultsFile);
			FileWriter outDataWriter = new FileWriter(outDataFile);
			FileWriter irResWriter = new FileWriter(irResultsFile);
			ArrayList<String> inputLines = new ArrayList<>();
			FileUtil.readLines(inputDataFile, inputLines);
			int instanceID = 0;
			for(String line : inputLines){
				String [] tokens = line.split("\t");
				//label context candidate_response
				prfInitRetrievalResponseExpansion(tokens, analyzer, searcher,hitsPerPage, expandTermNum, irResWriter, outDataWriter, instanceID, searchFieldInPosts);
				instanceID++;
				if(instanceID % 1000 == 0){
					System.out.println("cur query/instance id: " + instanceID);
					LocalDateTime now = LocalDateTime.now();
					System.out.println("current time: " + dtf.format(now));
				}
			}
			outDataWriter.close();
			irResWriter.close();
		}
	}

	//Given a (label \t context \t response) triple, do response expansion with PRF
	//Retrieve top K Q&A posts from external CQA collection (SO&AskUbuntu) using the candidate
	//response r_k as the query
	//Generate the expanded responses with the most frequent (by TF) terms or the most specific
	//terms (by IDF) extracted from the top K Q&A posts. Currently use the most frequent terms 
	//(by TF) as in RM1/RM3/Guihong, Jian-Yun, Jianfeng, et al. (SIGIR08). Will try more variations later
	//instanceID is the ID for the train/valid/test instance
	private static void prfInitRetrievalResponseExpansion(String[] tokens, Analyzer analyzer, IndexSearcher searcher,
			int hitsPerPage, int expandTermNum, FileWriter irResWriter, FileWriter outDataWriter, int instanceID, String searchFieldInPosts) throws ParseException, IOException {
		// TODO Auto-generated method stub
		String label = tokens[0];
		String context = tokens[1];
		String candidateResp = tokens[2];
		
		String query = candidateResp; // use the candidate response as the query 
		ArrayList<String> qWords = new ArrayList<>();
		FileUtil.tokenizeStanfordTKAndLowerCase(query, qWords, true); //tokenization, remove punctuation, lower case, remove stop words (if set to true)
		String qString = "";
		for(String qw : qWords){
			qString += " " + qw;
		}
		if(qString.trim().equals("") || qString.trim() == null) {
			outDataWriter.append(label + "\t" + context + "\t" + candidateResp + "\n");//In this case, we do not do expansion
		    outDataWriter.flush();
			return; //If qString is null (question words are all stop words), return. We do not do expansion now.
		}
		BooleanQuery.setMaxClauseCount(102400);
		Query q = new QueryParser(searchFieldInPosts, analyzer).parse(QueryParser.escape(qString));// Can search bodyText or titleText of SO posts
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
		searcher.search(q, collector);
	    ArrayList<ScoreDoc> hitsList = new ArrayList<ScoreDoc>();
	    for(ScoreDoc s : collector.topDocs().scoreDocs){
	    	hitsList.add(s);
	    }
	    //find the most frequent expandTermNum terms from top retrieved documents in histsList to do response expansion
	    //output the top retrieved documents
	    HashMap<String, Integer> termToTFMap = new HashMap<String, Integer>();
	    for(int i = 0; i < hitsList.size(); i++){
	    	int docId = hitsList.get(i).doc;
		    Document d = searcher.doc(docId);
		    //instanceID \t label \t candidateResp \t informationOfD
		    irResWriter.append(instanceID + "\t" + label + "\t" + query + "\t" + d.get("PostID") + 
		    		"\t" + d.get("PostTypeId") + "\t" + d.get("titleText") + "\n");	
		    irResWriter.flush();
		    String topRankedDocText = d.get(searchFieldInPosts);
		    updateTermToTFMap(termToTFMap, topRankedDocText);
	    }
	    //System.out.println("test termToTFMap: " + termToTFMap);
	    TreeMap<String, Integer> sortedmap = sortMapByValue(termToTFMap);
	    //System.out.println("current query: " + query);
	    StringBuilder expandedTermsSB = new StringBuilder();
	    for(String term : sortedmap.keySet()){
	    	if(term.trim().equals("")) continue;
	    	//System.out.println("test term and count: " + term + "\t" + termToTFMap.get(term));
	    	expandedTermsSB.append(term.trim() + " ");
	    	expandTermNum--;
	    	if(expandTermNum <= 0) {
	    		//System.out.println("output top K frequent terms done! ");
	    		break;
	    	}
	    }
	    String expandedResp = candidateResp + " " + expandedTermsSB.toString();
	    outDataWriter.append(label + "\t" + context + "\t" + expandedResp + "\n");
	    outDataWriter.flush();
	}

	private static void updateTermToTFMap(Map<String, Integer> termToTFMap, String topRankedDocText) {
		// TODO Auto-generated method stub
		ArrayList<String> dWords = new ArrayList<>();
		FileUtil.tokenizeStanfordTKAndLowerCase(topRankedDocText, dWords, true); 
		for(String dw : dWords){
			termToTFMap.put(dw, termToTFMap.getOrDefault(dw, 1) + 1);
		}
	}
	
	public static TreeMap<String, Integer> sortMapByValue(HashMap<String, Integer> map){
		Comparator<String> comparator = new ValueComparator(map);
		//TreeMap is a map sorted by its keys. 
		//The comparator is used to sort the TreeMap by keys. 
		TreeMap<String, Integer> result = new TreeMap<String, Integer>(comparator);
		result.putAll(map);
		return result;
	}
}


