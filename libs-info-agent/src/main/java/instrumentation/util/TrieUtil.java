package instrumentation.util;

import java.util.HashMap;

class TrieNode {
	String str;
    HashMap<String, TrieNode> children = new HashMap<String, TrieNode>();
    boolean isClass;
    
    public TrieNode() {}
    
    public TrieNode(String str){
        this.str = str;
    }
}

public class TrieUtil {
	private TrieNode root;
	
	public TrieUtil() {
        root = new TrieNode();
    }
	
	public void insert(String className) {
		HashMap<String, TrieNode> children = root.children;
		String[] strs = className.split("\\.");
	    for (int i=0; i<strs.length; i++) {
	    	String str = strs[i];
	    	TrieNode tn;
	    	if (children.containsKey(str))
	    		tn = children.get(str);
	    	else {
	    		tn = new TrieNode(str);
	    		children.put(str, tn);
	    	}
	    	children = tn.children;
	    	if (i == strs.length-1)
	    		tn.isClass = true;
	    }
	}

    public boolean containsNode(String className) {
        TrieNode tn = searchNode(className);
        return (tn!=null && tn.isClass) ? true : false;
    }

    public TrieNode searchNode(String className) {
    	HashMap<String, TrieNode> children = root.children;
    	String[] strs = className.split("\\.");
    	TrieNode tn = null;
    	for (int i=0; i<strs.length; i++) {
	    	String str = strs[i];
	    	if (children.containsKey(str)) {
	    		tn = children.get(str);
	    		children = tn.children;
	    	}
	    	else
	    		return null;
	    }
    	
    	return tn;
    }
}
