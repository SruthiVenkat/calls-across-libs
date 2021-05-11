package animal;
import static_dynamic_call_check.Animal;
import static_dynamic_call_check.Dog;

public class AnimalClient {
	public void animalClientMethod() {
		Animal a = new Dog();
		a.sayHello();
	}
}
