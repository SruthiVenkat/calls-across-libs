package types;

import misc.Client;
import packageA.RandomTypes;

public class TypesClient {

	public static void main(String[] args) {
		TypesClient typesClient = new TypesClient();
		typesClient.typesClientMethod();
	}
	
	public void typesClientMethod() {
		RandomTypes rtypes = new RandomTypes();
		System.out.println("RandomTypes boolean - "+rtypes.getBoolean());
		System.out.println("RandomTypes char - "+rtypes.getChar());
		
		TypesClient tClient = new TypesClient();
		tClient.clientMethod(rtypes);
	}

	public void clientMethod(RandomTypes rtypes) {
		Client.clientMethod1(rtypes);
		
		System.out.println("RandomTypes string - "+rtypes.getString(5));
	}
}
