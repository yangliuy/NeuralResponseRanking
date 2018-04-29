package scripts;
import java.io.*;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.dom4j.ElementHandler;
import org.dom4j.ElementPath;
import org.dom4j.io.XMLWriter;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

/**Test the functions of Dom4j, especially on reading/writing 
 * big XML files as a pre-test of parsing of SO files
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class Dom4jTest {

     public static void main(String[] args) {

          try {

        	   Dom4jTest modelReader = new Dom4jTest();
               //common xml read and write
//             modelReader.testWrite();
//             modelReader.testRead();
              
               //write big xml file
               //modelReader.writeToBigFile("data/XMLTest/", "bigXML.xml");
               //read big xml file
               SAXReader reader = new SAXReader();
               reader.setDefaultHandler(new ElementHandler() {
                    public void onEnd(ElementPath ep) {
                         System.out.println("see end label!");
                         Element e = ep.getCurrent();
                         System.out.println("PostID: " + e.attributeValue("Id"));
                         System.out.println("CreationDate: " + e.attributeValue("CreationDate"));
                         System.out.println("PostTypeId: " + e.attributeValue("PostTypeId"));
                         System.out.println("AcceptedAnswerId: " + e.attributeValue("AcceptedAnswerId"));
                         System.out.println("ParentId: " + e.attributeValue("ParentId"));
                         System.out.println("Score: " + e.attributeValue("Score"));
                         System.out.println("ViewCount: " + e.attributeValue("ViewCount"));
                         System.out.println("AnswerCount: " + e.attributeValue("AnswerCount"));
                         System.out.println("Title: " + e.attributeValue("Title"));
                         System.out.println("Tags: " + e.attributeValue("Tags"));
                         System.out.println("Body: " + e.attributeValue("Body"));
                         e.detach();
                    }

                    public void onStart(ElementPath arg0) {
                         //System.out.println("see start label!");
                    }
               });
              
               Document doc = reader.read(new BufferedInputStream(
                         new FileInputStream(new File("data/ExternalCollection/Posts.xml"))));

          } catch (Exception e) {
               e.printStackTrace();
          }

     }

//     public void testRead() throws Exception {
//
//          SAXReader reader = new SAXReader();
//
//          Document document = reader.read(new FileInputStream("output.xml"));
//
//          Element root = document.getRootElement();
//
//          for (Iterator iter = root.elementIterator(); iter.hasNext();) {
//               Element element = (Element) iter.next();
//               System.out.println(element.getText());
//               System.out.println(element.attributeValue("name"));
//               System.out.println(element.attributeValue("blog"));
//          }
//
//     }
//
//     public void testWrite() throws Exception {
//
//          Document document = DocumentHelper.createDocument();
//
//          Element root = document.addElement("root");
//
//          Element element1 = root.addElement("user")
//                    .addAttribute("name", "Alexander")
//                    .addAttribute("blog", "http://netnova.blogjava.net")
//                    .addText("I love China!");
//
//          XMLWriter writer = new XMLWriter(new FileOutputStream("output.xml"));
//
//          writer.write(document);
//          writer.close();
//
//     }

     public void writeToBigFile(String filePath, String fileName) {
          Document doc = DocumentHelper.createDocument();
          Element rootElement = doc.addElement("root");
          rootElement.addAttribute("name", "root");
          Element testElement = DocumentHelper.createElement("eleTest");
          testElement.addAttribute("name", "testEle");
          testElement.setParent(rootElement);
          XMLWriter out = null;
          try {
               out = new XMLWriter(new FileWriter(new File(filePath
                         + File.separator + fileName)));
               out.startDocument();
               out.writeOpen(rootElement);
               for (int i = 0; i <= 10000000; i++) {
                    out.write(testElement);
               }
               out.writeClose(rootElement);
          } catch (SAXException e) {
               e.printStackTrace();
          } catch (IOException e) {
               e.printStackTrace();
          } finally {
               if (out != null) {
                    try {
                         out.close();
                    } catch (IOException e) {
                         // TODO Auto-generated catch block
                         e.printStackTrace();
                    }
               }
          }
     }
}