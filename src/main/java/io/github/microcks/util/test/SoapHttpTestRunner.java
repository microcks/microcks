/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util.test;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Service;
import io.github.microcks.util.SoapMessageValidator;
import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;

/**
 * An extension of HttpTestRunner that checks that returned response is
 * valid according the SOAP contract definition.
 * 
 * @see SoapMessageValidator
 * @author laurent
 */
public class SoapHttpTestRunner extends HttpTestRunner{
   
	/** A simple logger for diagnostic messages. */
	private static Logger log = LoggerFactory.getLogger(SoapHttpTestRunner.class);

	/** The URL of resources used for validation. */
	private String resourceUrl = null;

	/**
	 * The URL of resources used for validation. 
	 * @return The URL of resources used for validation
	 */
	public String getResourceUrl(){
		return resourceUrl;
	}

	/**
	 * The URL of resources used for validation.
	 * @param resourceUrl The URL of resources used for validation.
	 */
	public void setResourceUrl(String resourceUrl){
		this.resourceUrl = resourceUrl;
	}

	/**
    * Build the HttpMethod corresponding to string. Always POST for a SoapHttpTestRunner.
    */
   @Override
   public HttpMethod buildMethod(String method){
      return HttpMethod.POST;
   }
	
	@Override
	protected int extractTestReturnCode(Service service, Operation operation, Request request, ClientHttpResponse httpResponse){
		int code = TestReturn.SUCCESS_CODE;

		// Vérification du code retour HTTP : délégation à la super-classe.
		code = super.extractTestReturnCode(service, operation, request, httpResponse);
		// Si le test est en erreur, pas la peine de continuer...
		if (TestReturn.FAILURE_CODE == code){
			return code;
		}

		// Identification du jeu de caractères de la Réponse.
		String charset = null;	// par défaut : jeu de caractères par défaut de la plateforme.
		MediaType mediaType = httpResponse.getHeaders().getContentType();
		if (mediaType != null){
			Charset cs = mediaType.getCharSet();
			if (cs != null){
				charset = cs.name();
			}
		}

		// Extract body response.
		StringWriter writer = new StringWriter();
		try{
			IOUtils.copy(httpResponse.getBody(), writer, charset);
		} catch (IOException ioe) {
			log.debug("IOException while getting body content into response", ioe);
			return TestReturn.FAILURE_CODE;
		}

		try{
			// Validate Soap message body according to operation output part.
			List<XmlError> errors = SoapMessageValidator.validateSoapMessage(
               operation.getOutputName(), service.getXmlNS(), writer.toString(), resourceUrl
                     + service.getName() + "-" + service.getVersion() + ".wsdl", true
         );

			if (!errors.isEmpty()){
				log.debug("Soap validation errors found " + errors.size() + ", marking test as failed.");
				return TestReturn.FAILURE_CODE;
			}
		} catch (XmlException e) {
			log.debug("XmlException while validating Soap response message", e);
			return TestReturn.FAILURE_CODE;
		}
		return code;
	}
}
