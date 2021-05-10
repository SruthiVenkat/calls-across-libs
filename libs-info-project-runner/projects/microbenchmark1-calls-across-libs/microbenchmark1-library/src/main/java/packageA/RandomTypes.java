package packageA;

import java.util.Random;

public class RandomTypes {
	private static Random random = new Random();

	public int getInt() {
		return random.nextInt();
	}

	public boolean getBoolean() {
		return random.nextBoolean();
	}
	
	public char getChar() {
		int min = 32, max = 126;
		return (char)(random.nextInt((max - min) + 1) + min);
	}
	
	public double getDouble() {
		return random.nextDouble();
	} 
	
	public String getString(int length) {
		String str = "";
		for (int i=0; i<length; i++)
			str+=getChar();
		return str;
	}
} 
