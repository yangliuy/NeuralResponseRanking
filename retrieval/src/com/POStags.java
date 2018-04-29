package com;

import java.util.ArrayList;
import java.util.HashMap;


public class POStags {
	
	public HashMap<String, String> gTagMap;
	
	public POStags() {
		String map = "CD ADJ" + "\t" +
		"JJ ADJ" + "\t" +
		"JJR ADJ" + "\t" +
		"JJS ADJ" + "\t" +
		"VB V" + "\t" +
		"VBD V" + "\t" +
		"VBG V" + "\t" +
		"VBN V" + "\t" +
		"VBP V" + "\t" +
		"VBZ V" + "\t" +
		"MD V" + "\t" +
		"NN N" + "\t" +
		"NNS N" + "\t" +
		"NNP N" + "\t" +
		"NNPS N" + "\t" +
		"RB ADV" + "\t" +
		"RBR ADV" + "\t" +
		"RBS ADV" + "\t" +
		"RP ADV" + "\t" +
		"WRB ADV" + "\t" +
		"DT DET" + "\t" +
		"PDT DET" + "\t" +
		"WDT DET" + "\t" +
		"POS DET" + "\t" +
		"PRP PRP" + "\t" +
		"WP PRP" + "\t" +
		"PRP$ PRP$" + "\t" +
		"WP$ PRP$" + "\t" +
		"TO PREP" + "\t" +
		"IN PREP" + "\t" +
		"CC CONJ" + "\t" +
		"EX OTHER" + "\t" +
		"FW OTHER" + "\t" +
		"SYM OTHER" + "\t" +
		"UH OTHER" + "\t" +
		"LS OTHER" + "\t";
		
		gTagMap = new HashMap<String, String>();
		String [] maps = map.split("\t");
		ArrayList<String> tokens = new ArrayList<String> ();
		for(int i = 0; i < maps.length; i++) {
			tokens.clear();
			FileUtil.tokenize(maps[i], tokens);
			if(tokens.size() != 2) {
				System.err.println(maps[i]);
			} else {
				gTagMap.put(tokens.get(0).toLowerCase().trim(),
						tokens.get(1).toLowerCase().trim());
			}
		}
	}
}
