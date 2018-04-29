package com;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class ValueComparator implements Comparator<String>{
	 
	Map<String, Integer> map = new HashMap<String, Integer>();
 
	public ValueComparator(HashMap<String, Integer> map){
		this.map.putAll(map);
	}
 
	@Override
	public int compare(String s1, String s2) {
		if(map.get(s1) >= map.get(s2)){
			return -1;
		}else{
			return 1;
		}	
	}
}
