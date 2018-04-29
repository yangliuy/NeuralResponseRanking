package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import com.DataBaseConnection;
import com.FileUtil;

import scripts.DataStatUttDialogLength;

/**Data statistics for train/valid/test data of MS dialog data
 * Make use of SQL to speed up the coding of data statistics
 * 
 * @author Liu Yang
 * @email  lyang@cs.umass.edu
 * @homepage https://sites.google.com/site/lyangwww/
 */

public class TrainValidTestDataStats {
	
	static DataBaseConnection dbc = new DataBaseConnection();

	public static void main(String args[]) throws IOException{
		
		String dataPath = "data/";
		
		String MSQADialogDataPath = dataPath + "MicrosoftCSQA/v1-2/";
		String dialogTitleFile = MSQADialogDataPath + "dialog_titles.tsv";
		String dialogContentFilteredFile = MSQADialogDataPath + "dialog_contents_filtered.tsv";
		String trainFile = MSQADialogDataPath + "train_before_filter_min_tp.tsv";
		String validFile = MSQADialogDataPath + "valid_before_filter_min_tp.tsv";
		String testFile = MSQADialogDataPath + "test_before_filter_min_tp.tsv";
		//insertTSVIntoSQLTable(trainFile, "dialog_contents_train_tmp");
		//insertTSVIntoSQLTable(validFile, "dialog_contents_valid_tmp");
		//insertTSVIntoSQLTable(testFile, "dialog_contents_test_tmp");
		//generateFinalTrainDevTestData(MSQADialogDataPath);
		// Data statistics for number of words/tokens in train/valid/test data
		DataStatUttDialogLength.countWordsInDialogs(MSQADialogDataPath + "train.tsv");
		DataStatUttDialogLength.countWordsInDialogs(MSQADialogDataPath + "valid.tsv");
		DataStatUttDialogLength.countWordsInDialogs(MSQADialogDataPath + "test.tsv");
	}

	private static void generateFinalTrainDevTestData(String mSQADialogDataPath) throws IOException {
		// TODO Auto-generated method stub
		//1. Filter *__filter_min_tp.tsv by the legal_dialog_id files, which contains legal dialogs fitted in min/max turns/participants constrain
		//filterDialogs(mSQADialogDataPath, "train");
		//filterDialogs(mSQADialogDataPath, "valid");
		//filterDialogs(mSQADialogDataPath, "test");
		
		//2. Insert the final train.tsv/valid.tsv/test.tsv files into DB tables
		insertTSVIntoSQLTable(mSQADialogDataPath + "train.tsv", "dialog_contents_train");
		insertTSVIntoSQLTable(mSQADialogDataPath + "valid.tsv", "dialog_contents_valid");
		insertTSVIntoSQLTable(mSQADialogDataPath + "test.tsv", "dialog_contents_test");
	}

	private static void filterDialogs(String mSQADialogDataPath, String dataLabel) {
		// TODO Auto-generated method stub
		String inputFile = mSQADialogDataPath + dataLabel + "_before_filter_min_tp.tsv";
		String outputFile = mSQADialogDataPath + dataLabel + ".tsv";
		String legalDialogIDFile = mSQADialogDataPath + "legal_dialog_id_" + dataLabel + ".tsv";
		System.out.println("cur write file: " + outputFile);
		HashSet<String> selectedIDs = new HashSet<String>();
		ArrayList<String> idLines = new ArrayList<String>();
		FileUtil.readLines(legalDialogIDFile, idLines);
		for(String id : idLines){
			selectedIDs.add(id);
		}
		ArrayList<String> inputLines = new ArrayList<String>();
		ArrayList<String> outputLines = new ArrayList<String>();
		FileUtil.readLines(inputFile, inputLines);
		for(String l : inputLines){
			if(selectedIDs.contains(l.split("\t")[1])){
				outputLines.add(l);
			}
		}
		FileUtil.writeLines(outputFile, outputLines);
	}

	private static void insertTSVIntoSQLTable(String tsvFileName, String tableName) throws IOException {
		// TODO Auto-generated method stub
		BufferedReader tsvBR = new BufferedReader(new FileReader(tsvFileName));
		String line = null;
		int lineCount = 0;
		while((line = tsvBR.readLine()) != null){
			lineCount++;
			if(lineCount % 5000 == 0){
				System.out.println("curLineCount: " + lineCount);
			}
			String [] tokens = line.split("\t");
			String sql = "insert into "+ tableName +"(id, dialog_id, utterance_id, user_or_agent, user_id,"
					+ " vote, answer_time, affiliation, isAnswer, utterance) values('"+ tokens [0] +"','"+ tokens [1] +"',"
					+ "'"+ tokens [2] +"','"+ tokens [3] +"','"+ tokens[4].replaceAll("'", "''") +"','"+ tokens [5] +"','"+ tokens [6] +"','"
					+ ""+ tokens[7].replaceAll("'", "''") +"','"+ tokens [8] +"','"+ tokens[9].replaceAll("'", "''") +"')";
			dbc.executeUpdate(sql);
		}
	}
}
