package scripts;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.FileUtil;

import parser.StanfordTokenizer;

/**Data preprocess of Microsoft QA dialog data
 * 1. Remove duplicated utterances (Done)
 * 2. Filter users who are asking the same question in the QA dialog
 *    Adopt a simple rule based approach for this preprocessing step
 *    Define a filtering key word list: "same problems", "same issues", etc
 *    Observe data and add more filtering key words like these words
 *    Filter utterance where the first sentence contains these words (Done)
 * 3. Remove dialogs where there are no responses by agents or less than 3 turns
 *    Should beyond 1Q1A (Multi-turn QA dialog)
 * 4. Finish the tokenization of Microsoft QA dialogs
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class MicrosoftQADialogDataPreprocess {
	
	public static String filterWordsFile = "data/MicrosoftCSQA/filterUtteranceKeyWords.txt";
	
	public static void main(String args[]) throws IOException{
		
		String dataPath = "data/";
		
		String MSQADialogDataPath = dataPath + "MicrosoftCSQA/v1-2/";
		String dialogContentFile = MSQADialogDataPath + "dialog_contents.tsv";
		String dialogTitleFile = MSQADialogDataPath + "dialog_titles.tsv";
		String dialogContentFilteredFile = MSQADialogDataPath + "dialog_contents_filtered.tsv";
		
		filterUtterances(dialogContentFile, dialogContentFilteredFile);
	}

	//filter duplicated utterances and filter utterances by users who are asking the same question
	//instead of providing solutions
	private static void filterUtterances(String dialogContentFile, String dialogContentFilteredFile) throws IOException {
		// TODO Auto-generated method stub
		BufferedReader dialogBR = new BufferedReader(new FileReader(dialogContentFile));
		FileWriter dialogWriter = new FileWriter(dialogContentFilteredFile);
		Set<String> filterPhraseSet = new HashSet<>();
		initFilterPhraseSet(filterPhraseSet);
		
		String line = null;
		int lineCount = 0;
		Set<String> uniqueUtteranceInDialog = new HashSet<String>();
		String curDialogID = "1";
		while((line = dialogBR.readLine()) != null){
			lineCount++;
			if(lineCount % 5000 == 0){
				System.out.println("curLine count: " + lineCount);
			}
			//if(lineCount > 1000) break;
			//System.out.println(line);
			String[] tokens = line.split("\t");
			if(tokens.length < 9) continue; //the content is null
			String uttContent = tokens[8];
			//Check whether there are "filtered words" such as
			//"some problems", "some issues" in the first sentence of the utterance
			ArrayList<String> tokenizedUtt = StanfordTokenizer.tokenizeSents(uttContent);
			if(tokenizedUtt.size() < 1) continue;
			boolean shouldFilter = false;
			for(String p : filterPhraseSet){
				if(tokenizedUtt.get(0).toLowerCase().contains(p)){
					shouldFilter = true;
					break;
				}
			}
			if(shouldFilter) continue;
			String uttContentTokenized = "";
			for(String uct : tokenizedUtt){
				uttContentTokenized += uct + " ";
			}
			line = tokens[0] + "\t" + tokens[1] + "\t" + tokens[2] + "\t" + tokens[3] + 
					"\t" + tokens[4] + "\t" + tokens[5] + "\t" +
					tokens[6] + "\t" + tokens[7] + "\t" + uttContentTokenized;
			if(tokens[0].equals(curDialogID)){
				//Same dialog
				//1038
				if(uniqueUtteranceInDialog.contains(uttContentTokenized)){
					continue;//Skip duplicated utterance
				} else{
					dialogWriter.append(line + "\n");
					uniqueUtteranceInDialog.add(uttContentTokenized);
				}
			} else{
				//New dialog
				dialogWriter.append(line + "\n");
				curDialogID = tokens[0];
				uniqueUtteranceInDialog.clear();
				uniqueUtteranceInDialog.add(uttContentTokenized);
			}
		}
		dialogWriter.flush();
		dialogWriter.close();
	}

	private static void initFilterPhraseSet(Set<String> filterPhraseSet) {
		// TODO Auto-generated method stub
		ArrayList<String> lines = new ArrayList<>();
		FileUtil.readLines(filterWordsFile, lines);
		for(String l : lines){
			filterPhraseSet.add(l);
		}
	}
}
