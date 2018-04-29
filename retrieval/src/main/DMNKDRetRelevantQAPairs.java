package main;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

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

/**Implementation of the relevant QA pair retrieval for QA co-occurrence matrix extraction
 * 1. Retrieve top K Q&A posts from external CQA collection (SO&AskUbuntu) using the candidate
 *    response r_i^k as the query
 * 2. Generate the QA term co-occurrence matrix with the retrieved QA pairs
 * 3. Run DMN-KD model with the QA term co-occurrence matrix
 * 4. For MSDialog, use SOTwoYear as external knowledge source; For Ubuntu, use askUbuntu as external 
 *    knowledge source
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class DMNKDRetRelevantQAPairs {
	
public static void main(String[] args) throws IOException, ParseException{
		
		//int hitsPerPage = 20 or 100; // Target on getting 10 question posts **with** accepted answers. Thus we should set bigger hitsPerPage here
		//String searchFieldInPosts = "bodyText"; // Can search bodyText or titleText of SO posts
		if(args.length < 3){
	    	System.out.println("please input params: hitsPerPage searchFieldInPosts dataName");
	    	System.exit(1);
	    }
		int hitsPerPage = Integer.valueOf(args[0]);
	    String searchFieldInPosts = args[1], dataName = args[2];// the dataName could be ms or udc
	    System.out.println("input params hitsPerPage searchFieldInPosts dataName: " + hitsPerPage + "\t" + searchFieldInPosts + "\t" + dataName);
		String modelInputDataPath = "../../PycharmProjects/NLPIRNNMatchZooQA/src-match-zoo-lyang-dev/data/" + dataName + "/ModelInput/";
		String indexPath = "";
		if(dataName.equals("ms")){
			indexPath = "data/ExternalCollection/stackoverflow/so-index-qpostswithaccepta-two-years/"; // on Goulburn
		} else{
			indexPath = "data/ExternalCollection/askubuntu/askubuntu-index-qpostswithaccepta-whole/"; // on Goulburn
		}

		Analyzer analyzer = new StandardAnalyzer();
		Directory indexDir = FSDirectory.open(Paths.get(indexPath));
	    IndexReader indexReader = DirectoryReader.open(indexDir);
	    IndexSearcher searcher = new IndexSearcher(indexReader);
	    searcher.setSimilarity(new BM25Similarity());
	    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		
		for (String dataPart : new String[]{"train", "valid", "test"}){
			String inputDataFile = modelInputDataPath + dataPart + ".txt.mz";// input file format
			String irResultsFile = modelInputDataPath + dataPart + "_dmnkd_ir_res_qpwaa_index_" + searchFieldInPosts + ".txt"; // the file storing the query and retrieval results in the IR module of dmn-kd
			System.out.println("generate irResultsFile" + irResultsFile);
			FileWriter irResWriter = new FileWriter(irResultsFile);
			ArrayList<String> inputLines = new ArrayList<>();
			FileUtil.readLines(inputDataFile, inputLines);
			int instanceID = 1; // add train/valid/test in instanceID to discriminate instances in train/valid/test files
			for(String line : inputLines){
				String [] tokens = line.split("\t");
				//label context candidate_response
				dmnKDIRQAPosts(tokens, analyzer, searcher,hitsPerPage, irResWriter, instanceID, searchFieldInPosts, dataPart);
				instanceID++;
				if(instanceID % 1000 == 0){
					System.out.println("cur dataPart and query/instance id: " + dataPart + "\t" + instanceID);
					LocalDateTime now = LocalDateTime.now();
					System.out.println("current time: " + dtf.format(now));
				}
			}
			irResWriter.close();
		}
	}

	//Given a (label \t context \t response) triple, do response expansion with PRF
	//Retrieve top K Q&A posts from external CQA collection (SO&AskUbuntu) using the candidate
	//response r_i^k as the query
	//instanceID is the ID for the train/valid/test instance
	//add train/valid/test in instanceID to discriminate instances in train/valid/test files
	//will merge the three train/valid/test files into a single file
	private static void dmnKDIRQAPosts(String[] tokens, Analyzer analyzer, IndexSearcher searcher,
			int hitsPerPage, FileWriter irResWriter, int instanceID, String searchFieldInPosts, String dataPart) throws ParseException, IOException {
		// TODO Auto-generated method stub
		String label = tokens[0];
		//String context = tokens[1];
		String candidateResp = tokens[2];
		
		String query = candidateResp; // use the candidate response as the query 
		ArrayList<String> qWords = new ArrayList<>();
		FileUtil.tokenizeStanfordTKAndLowerCase(query, qWords, true); //tokenization, remove punctuation, lower case, remove stop words (if set to true)
		String qString = "";
		for(String qw : qWords){
			qString += " " + qw;
		}
		if(qString.trim().equals("") || qString.trim() == null) {
			return; //If qString is null (question words are all stop words), return.
		}
		BooleanQuery.setMaxClauseCount(102400);
		Query q = new QueryParser(searchFieldInPosts, analyzer).parse(QueryParser.escape(qString));// Can search bodyText or titleText of SO posts
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
		searcher.search(q, collector);
	    ArrayList<ScoreDoc> hitsList = new ArrayList<ScoreDoc>();
	    for(ScoreDoc s : collector.topDocs().scoreDocs){
	    	hitsList.add(s);
	    }
	    
	    //output the top retrieved documents
	    for(int i = 0; i < hitsList.size(); i++){
	    	int docId = hitsList.get(i).doc;
		    Document d = searcher.doc(docId);
		    //instanceID \t label \t candidateResp \t informationOfD
		    irResWriter.append(dataPart + "_" + instanceID + "\t" + d.get("PostID") + 
		    		"\t" + d.get("PostTypeId")  + "\n");	// remove label, query and titleText to save disk space
		    irResWriter.flush();
		    //String topRankedDocText = d.get(searchFieldInPosts);
	    }
	}
}
