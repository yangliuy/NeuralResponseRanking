package com;

public class FuncUtils {

    public static int convertLabelToScore(String label) {
        int score = 0;
        if(label.equals("<PERFECT>")) score = 4;
        else if(label.equals("<EXCEL>")) score = 3;
        else if(label.equals("<GOOD>")) score = 2;
        else if(label.equals("<FAIR>")) score = 1;
        else if(label.equals("<NONE>")){
            
        } else {
            score = Integer.valueOf(label);
        }
        return score;
    }
}
