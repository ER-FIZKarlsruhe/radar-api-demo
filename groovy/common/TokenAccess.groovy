/*
 * Copyright 2018 FIZ Karlsruhe - Leibniz-Institut fuer Informationsinfrastruktur GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.json.JsonOutput

class Token {

    String userName = null;
    String userPassword = null;
    
    def binding = new Binding()
    def engine = new GroovyScriptEngine("./../common")
    def variables = engine.run('CommonVariables.groovy', binding)
    def httpRequester = engine.run('HttpRequester.groovy', binding)

    public Map getTokens(String role = null) throws IOException {
        setUser(role)

        // JsonOutput escapes quotes and backslashes in the credentials
        String body = JsonOutput.toJson([clientId: variables.clientId, clientSecret: variables.clientSecret,
                                         redirectUrl: variables.redirectUrl, userName: userName, userPassword: userPassword])
        return httpRequester.doPost(variables.contextUrl + "/api/tokens", body, null)
    }
    
    public Map refreshToken(String refreshToken, String role = null) throws IOException {
      setUser(role)

      String body = JsonOutput.toJson([clientId: variables.clientId, clientSecret: variables.clientSecret, refreshToken: refreshToken])
      return httpRequester.doPost(variables.contextUrl + "/api/tokens/refresh", body, null)
  }
    
    private void setUser(String role = null) {
        if (role != null && role.equals("admin")) {
            userName = variables.adminUserName
            userPassword = variables.adminUserPassword
        } else {
            userName = variables.contractAdminUserName
            userPassword = variables.contractAdminUserPassword
        }
    }
}

new Token()

