package dev.chinh.portfolio.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject the current user's ID from the JWT token into controller method parameters.
 * Use this annotation on method parameters of controller methods to automatically receive
 * the authenticated user's UUID.
 *
 * Example:
 * <pre>
 * @GetMapping("/user/data")
 * public ResponseEntity<UserData> getUserData(@CurrentUser UUID userId) {
 *     return userDataService.findByOwnerId(userId);
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
