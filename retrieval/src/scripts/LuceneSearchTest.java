package scripts;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.dom4j.DocumentException;

public class LuceneSearchTest {
	public static void main(String[] args) throws IOException, InterruptedException, ParseException, DocumentException {
		String basedModelInputDataPath = "data/MicrosoftCSQA/v1-2/";
		String dialogContentFile = basedModelInputDataPath + "dialog_contents.tsv";

		Analyzer analyzer = new StandardAnalyzer();
	    String indexPath = "data/ExternalCollection/stack-overflow-index-one-year/";
	    Directory indexDir = FSDirectory.open(Paths.get(indexPath));
		int hitsPerPage = 100;
	    IndexReader indexReader = DirectoryReader.open(indexDir);
	    IndexSearcher searcher = new IndexSearcher(indexReader);
	    searcher.setSimilarity(new BM25Similarity());
	    String query = "question";
	    Query q = new QueryParser("question", analyzer).parse(QueryParser.escape(query));
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
		//System.out.println("query: " + query);
		//System.out.println("collector: " + collector);
		searcher.search(q, collector);
	    ArrayList<ScoreDoc> hitsList = new ArrayList<ScoreDoc>();
	    for(ScoreDoc s : collector.topDocs().scoreDocs){
	    	hitsList.add(s);
	    }
	    System.out.println("Query: " + query + " \nFound " + hitsList.size() + " hits");
	    for(int i = 0; i < hitsList.size();i++){
	    	int docId = hitsList.get(i).doc;
		    Document d = searcher.doc(docId);
		    System.out.println(query + "\t" + d.get("PostID") + "\t" + d.get("PostTypeId")+ "\t" + d.get("bodyText") + "\n");
	    }
	}
}
