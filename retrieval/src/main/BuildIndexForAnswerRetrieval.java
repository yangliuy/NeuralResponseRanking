package main;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.ElementHandler;
import org.dom4j.ElementPath;
import org.dom4j.io.SAXReader;

/**1. Build search Index for Wiki/Stack Overflow dump to retrieve
 *    relevant QA pairs
 *    Build index by parsing XML files
 * 2. DMN-PRF (V4-1) use the candidate answer responses as queries to retrieve relevant QA pairs
 *    to do response expansion
 *    DMN-KD-CQA (V4-2) use the question in the conversation as the query to retrieve relevant QA pairs
 *    to extract QA connection co-occurrence matrix from them
 * 3. Only index question posts (include both question title and question bodyText) in Stack-Overflow. In this way,
 *    we can support retrieve the title or the bodyText of question posts (Learn from the settings of DSSM/CDSSM). Once
 *    get the top K relevant questions, we can extract the corresponding accepted answers/all answers from SO database
 * 4. As another option, for DMN-PRF, we can index all Q&A posts and retrieve the top K Q&A posts by candidate answer responses
 *    to do the response expansion. This is only necessary for DMN-PRF.
 * 5. Use two year dump to retrieve relevant QA pairs for response expansion and
 *    QA connection knowledge distillation
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class BuildIndexForAnswerRetrieval {
	
	public static long indexedQAPostNum = 0;
	
	public static void main(String[] args) throws IOException, InterruptedException, ParseException, DocumentException {
		//String basedModelInputDataPath = "data/MicrosoftCSQA/v1-2/";
		
		//1. Build index for Stack Overflow & Wiki (just need to run once)
		Analyzer analyzer = new StandardAnalyzer();
//	    String indexPath = "data/ExternalCollection/stack-overflow-index-half-year/";
//	    String indexFileName = "data/ExternalCollection/PostsXMLSplitedFiles/PostsSince201703HalfYear/PostsHalfYearSO.xml";
	    if(args.length < 2){
	    	System.out.println("please input params: indexPath indexFileName");
	    	System.exit(1);
	    }
	    String indexPath = args[0];
	    String indexFileName = args[1];
	    if(!new File(indexPath).exists()){ //If the index path is not existed, created it
	    	System.out.println("create index path: " + indexPath);
	    	new File(indexPath).mkdir();
	    }
	    Directory indexDir = FSDirectory.open(Paths.get(indexPath));
	    // Optional: for better indexing performance, if you
	    // are indexing many documents, increase the RAM
	    // buffer.  But if you do this, increase the max heap
	    // size to the JVM (eg add -Xmx512m or -Xmx1g):
	    //
	    // iwc.setRAMBufferSizeMB(1024.0);
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
	    
	    SAXReader reader = new SAXReader();
        reader.setDefaultHandler(new ElementHandler() {
             public void onEnd(ElementPath ep) {
                  //System.out.println("see end label!");
            	  indexedQAPostNum++;
            	  if(indexedQAPostNum % 100000 == 0){
     				System.out.println("cur indexed doc number: " + indexedQAPostNum);
     			  }
                  Element e = ep.getCurrent();
                  Document curD = new Document();
                  // use a string field for isbn because we don't want it tokenized
                  String id = e.attributeValue("Id"), postTypeId = e.attributeValue("PostTypeId"), bodyText = e.attributeValue("Body"), title = e.attributeValue("Title");
                  String answerCount = e.attributeValue("AnswerCount");
                  // Retrieve the title of question posts to let the information more compact 
                  // and reduce nosie (Learn from the Settings of DSSM/CDSSM)
                  // Once get the relevant question, we can get the corresponding answers as the response
                  // Only need to index the title and body field of question posts
                  if(id == null || id.equals("") ||
                	 postTypeId == null || postTypeId.equals("") || postTypeId.equals("2") || // only index QPosts
                	 bodyText == null || bodyText.equals("") ||
                	 title == null || title.equals("") ||
                	 answerCount == null || answerCount.equals("")){
                	 return;
                  }
                  // Filter questions which don't have answers
                  int answerCountInt = Integer.valueOf(answerCount);
                  if(answerCountInt <= 0) return;
                  
                  curD.add(new StringField("PostID", e.attributeValue("Id"), Field.Store.YES));
                  curD.add(new StringField("PostTypeId", e.attributeValue("PostTypeId"), Field.Store.YES));
//                curD.add(new StringField("AcceptedAnswerId", e.attributeValue("AcceptedAnswerId"), Field.Store.YES));
//                  curD.add(new StringField("ParentId", e.attributeValue("ParentId"), Field.Store.YES));
//                  curD.add(new StringField("Score", e.attributeValue("Score"), Field.Store.YES));
//                  curD.add(new TextField("Tags", e.attributeValue("Tags"), Field.Store.YES));
                  //If there are tags, add the tokens in tags into the title
                  String titleText = e.attributeValue("Title");
                  String tags = e.attributeValue("Tags");
                  if(tags != null && !tags.equals("")){
                	  titleText += tags.replaceAll("[<>]", " ");
                  }
                  curD.add(new TextField("titleText", titleText, Field.Store.YES));
                  curD.add(new TextField("bodyText", e.attributeValue("Body"), Field.Store.YES));
	          	  try {
					w.addDocument(curD);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
                  e.detach();
             }

             public void onStart(ElementPath arg0) {
                  //System.out.println("see start label!");
             }
        });
       
        org.dom4j.Document doc = reader.read(new BufferedInputStream(
                  new FileInputStream(new File(indexFileName))));
        //w.flush();
		w.close();
		System.out.println("build index done!");
	}
}
