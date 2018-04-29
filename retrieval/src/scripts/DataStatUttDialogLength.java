package scripts;

import java.io.IOException;
import java.util.ArrayList;

import com.FileUtil;

/**Compute statistics for utterance/dialog length by words
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class DataStatUttDialogLength {

	static long wordTotalNumber = 0;
	
	public static void main(String args[]) throws IOException{
			
			String dataPath = "data/";
			
			String MSQADialogDataPath = dataPath + "MicrosoftCSQA/v1-2/";
			String dialogTitleFile = MSQADialogDataPath + "dialog_titles.tsv";
			String dialogContentFilteredFile = MSQADialogDataPath + "dialog_contents_filtered.tsv";
			
			countWordsInDialogs(dialogContentFilteredFile);
	}

	public static void countWordsInDialogs(String dialogContentFilteredFile) {
		// TODO Auto-generated method stub
		wordTotalNumber = 0;
		System.out.println("count words in dialogContentFilteredFile : " + dialogContentFilteredFile);
		ArrayList<String> lines = new ArrayList<String>();
		FileUtil.readLines(dialogContentFilteredFile, lines);
		ArrayList<String> words = new ArrayList<String>();
		int count = 0;
		for(String l : lines){
			count++;
			if(count % 5000 == 0) {
				System.out.println("count: " + count);
			}
			String[] tokens = l.split("\t");
			if (tokens.length < 10) continue;
			words.clear();
			FileUtil.tokenizeStanfordTKAndLowerCase(tokens[9], words, false);
			wordTotalNumber += words.size();
		}
		System.out.println("total number of words: " + wordTotalNumber);
	}
}
