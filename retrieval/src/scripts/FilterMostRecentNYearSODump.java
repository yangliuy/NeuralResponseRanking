package scripts;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.ElementHandler;
import org.dom4j.ElementPath;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.xml.sax.SAXException;

/**1. We don't have to use all the SO dump as the answer source.
 *    We can filter SO QA posts in most recent N years for quickly
 *    building experimental pipeline (Done is better than perfect)
 * 2. According to answer retrieval performance, we can adjust the size
 *    and time range of filtered SO data dump used in experiments
 *    
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class FilterMostRecentNYearSODump {
	
	public static long indexedQAPostNum = 0;

	public static void main(String[] args) throws IOException, InterruptedException, ParseException, DocumentException {
		if(args.length < 1){
			System.out.println("Please input parameter: filter threshold year!");
			System.exit(1);
		}
		String minYear = args[0];
		String postFile = "data/ExternalCollection/Posts.xml";
		String filteredFile = "data/ExternalCollection/PostsSince" + minYear + ".xml";
		String thresholdTime = minYear + "-01-01T00:00:00.000";
		System.out.println("min create time: " + thresholdTime);
		LocalDateTime thresholdLDT = LocalDateTime.parse(thresholdTime);
		org.dom4j.Document doc1 = DocumentHelper.createDocument();
        Element rootElement = doc1.addElement("root");
        rootElement.addAttribute("name", "root");
        XMLWriter out = new XMLWriter(new FileWriter(filteredFile));
        try {
			out.startDocument();
		} catch (SAXException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
        out.writeOpen(rootElement);
        
		SAXReader reader = new SAXReader();
        reader.setDefaultHandler(new ElementHandler() {
             public void onEnd(ElementPath ep) {
                  //System.out.println("see end label!");
            	  indexedQAPostNum++;
            	  if(indexedQAPostNum % 100000 == 0){
     				System.out.println("cur read doc number: " + indexedQAPostNum);
     			  }
                  Element e = ep.getCurrent();
                  Document curD = new Document();
                  LocalDateTime createDateTime = LocalDateTime.parse(e.attributeValue("CreationDate"));
                  if(createDateTime.compareTo(thresholdLDT) > 0){
                	  //only write lines after the threshold time
                	  try {
                		e.setParent(rootElement);
						out.write(e);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
		          }
                  e.detach();
             }

             public void onStart(ElementPath arg0) {
                  //System.out.println("see start label!");
             }
        });
       
        org.dom4j.Document doc2 = reader.read(new BufferedInputStream(
                  new FileInputStream(new File(postFile))));
		//w.close();
		System.out.println("Output filtered file done: " + filteredFile);
		out.writeClose(rootElement);
		out.close();
	}
}
