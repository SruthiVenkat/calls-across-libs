package instrumentation.util;

import java.util.HashMap;

class TrieNode {
	char c;
    HashMap<Character, TrieNode> children = new HashMap<Character, TrieNode>();
    boolean isClass;
    
    public TrieNode() {}
    
    public TrieNode(char c){
        this.c = c;
    }
}

public class TrieUtil {
	private TrieNode root;
	
	public TrieUtil() {
        root = new TrieNode();
    }
	
	public void insert(String className) {
		HashMap<Character, TrieNode> children = root.children;
		//String[] strs = className.split("\\.");
	    for (int i=0; i<className.length(); i++) {
	    	//String str = strs[i];
	    	char c = className.charAt(i);
	    	TrieNode tn;
	    	if (children.containsKey(c))
	    		tn = children.get(c);
	    	else {
	    		tn = new TrieNode(c);
	    		children.put(c, tn);
	    	}
	    	children = tn.children;
	    	if (i == className.length()-1)
	    		tn.isClass = true;
	    }
	}

    public boolean containsNode(String className) {
        TrieNode tn = searchNode(className);
        return (tn!=null && tn.isClass) ? true : false;
    }

    public TrieNode searchNode(String className) {
    	if (className==null)
    		return null;
    	HashMap<Character, TrieNode> children = root.children;
    	//String[] strs = className.split("\\.");
    	TrieNode tn = null;
    	for (int i=0; i<className.length(); i++) {
	    	//String str = strs[i];
    		char c = className.charAt(i);
	    	if (children.containsKey(c)) {
	    		tn = children.get(c);
	    		children = tn.children;
	    	}
	    	else
	    		return null;
	    }
    	return tn;
    }
}
