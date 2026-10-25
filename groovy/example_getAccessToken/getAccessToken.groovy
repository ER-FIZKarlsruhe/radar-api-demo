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


def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def token = engine.run('TokenAccess.groovy', binding)

println "\nContractAdmin token:"
tokens = token.getTokens()
println tokens

println "\nRefreshtoken for ContractAdmin:"
def refreshtokens = token.refreshToken(tokens.refresh_token)
println refreshtokens
