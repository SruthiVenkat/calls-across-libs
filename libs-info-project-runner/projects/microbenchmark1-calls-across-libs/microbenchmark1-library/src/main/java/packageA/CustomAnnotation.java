package packageA;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CustomAnnotation {
}
