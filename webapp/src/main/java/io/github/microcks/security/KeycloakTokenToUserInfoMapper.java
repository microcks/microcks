package io.github.microcks.security;

import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Simpler mappe for transforming KeyclaokSecurityContext token into UserInfo bean.
 * @author laurent
 */
public class KeycloakTokenToUserInfoMapper {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(KeycloakTokenToUserInfoMapper.class);

   /** The name of token claim that contains the groups information. */
   public static final String MICROCKS_GROUPS_TOKEN_CLAIM = "microcks-groups";
   /** The name of the token resource that holds roles information. */
   public static final String MICROCKS_APP_RESOURCE = "microcks-app";

   /**
    * Maps the information from KeycloakSecurityContext tokens into a UserInfo instance.
    * @param context The current security context provided by Keycloak server adapter.
    * @return A new UserInfo with info coming from Keycloak tokens.
    */
   public static UserInfo map(KeycloakSecurityContext context) {
      AccessToken token = context.getToken();

      // Build groups string array if any.
      String[] microcksGroups = null;
      Object groups = context.getToken().getOtherClaims().get(MICROCKS_GROUPS_TOKEN_CLAIM);
      if (groups instanceof List) {
         Object[] objGroups = ((List) groups).toArray();
         microcksGroups = Arrays.copyOf(objGroups, objGroups.length, String[].class);
      }

      // Create and return UserInfo.
      UserInfo userInfo = new UserInfo(token.getName(), token.getPreferredUsername(), token.getGivenName(), token.getFamilyName(), token.getEmail(),
            token.getResourceAccess(MICROCKS_APP_RESOURCE).getRoles().stream().toArray(String[] ::new), microcksGroups);
      log.debug("Current user is: " + userInfo);
      return userInfo;
   }
}
