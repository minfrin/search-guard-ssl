/*
 * Copyright 2017 floragunn GmbH
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
 * 
 */

package com.floragunn.searchguard.ssl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.env.Environment;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.floragunn.searchguard.ssl.util.CertificateValidator;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;

public class CertificateValidatorTest {
    
    protected final Logger log = LogManager.getLogger(this.getClass());
    
    @Before
    public void setup(){
        //System.setSecurityManager(new SecurityManager());
    }
    
    @Test
    public void testStaticCRL() throws Exception {
        
        File staticCrl = getAbsoluteFilePathFromClassPath("crl/revoked.crl");
        Collection<? extends CRL> crls = null;
        try(FileInputStream crlin = new FileInputStream(staticCrl)) {
            crls = CertificateFactory.getInstance("X.509").generateCRLs(crlin);
        }
        
        Assert.assertEquals(crls.size(), 1);
        
        //trust chain incl intermediate certificates (root + intermediates)
        Collection<? extends Certificate> rootCas;
        final File trustedCas = getAbsoluteFilePathFromClassPath("chain-ca.pem");
        try(FileInputStream trin = new FileInputStream(trustedCas)) {
            rootCas =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(rootCas.size(), 2);

        //certificate chain to validate (client cert + intermediates but without root)
        Collection<? extends Certificate> certsToValidate;
        final File certs = getAbsoluteFilePathFromClassPath("crl/revoked.crt.pem");
        try(FileInputStream trin = new FileInputStream(certs)) {
            certsToValidate =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(certsToValidate.size(), 2);
        
        CertificateValidator validator = new CertificateValidator(rootCas.toArray(new X509Certificate[0]), crls);
        try {
            validator.validate(certsToValidate.toArray(new X509Certificate[0]));
            Assert.fail();
        } catch (CertificateException e) {
            Assert.assertTrue(e.getCause().getCause().getCause() instanceof CertificateRevokedException);
        }
    }
    
    @Test
    public void testStaticCRLOk() throws Exception {
        
        File staticCrl = getAbsoluteFilePathFromClassPath("crl/revoked.crl");
        Collection<? extends CRL> crls = null;
        try(FileInputStream crlin = new FileInputStream(staticCrl)) {
            crls = CertificateFactory.getInstance("X.509").generateCRLs(crlin);
        }
        
        Assert.assertEquals(crls.size(), 1);
        
        //trust chain incl intermediate certificates (root + intermediates)
        Collection<? extends Certificate> rootCas;
        final File trustedCas = getAbsoluteFilePathFromClassPath("chain-ca.pem");
        try(FileInputStream trin = new FileInputStream(trustedCas)) {
            rootCas =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(rootCas.size(), 2);

        //certificate chain to validate (client cert + intermediates but without root)
        Collection<? extends Certificate> certsToValidate;
        final File certs = getAbsoluteFilePathFromClassPath("node-0.crt.pem");
        try(FileInputStream trin = new FileInputStream(certs)) {
            certsToValidate =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(certsToValidate.size(), 3);
        
        CertificateValidator validator = new CertificateValidator(rootCas.toArray(new X509Certificate[0]), crls);
        try {
            validator.validate(certsToValidate.toArray(new X509Certificate[0]));
        } catch (CertificateException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
    
    @Test
    public void testNoValidationPossible() throws Exception {

        //trust chain incl intermediate certificates (root + intermediates)
        Collection<? extends Certificate> rootCas;
        final File trustedCas = getAbsoluteFilePathFromClassPath("chain-ca.pem");
        try(FileInputStream trin = new FileInputStream(trustedCas)) {
            rootCas =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(rootCas.size(), 2);

        //certificate chain to validate (client cert + intermediates but without root)
        Collection<? extends Certificate> certsToValidate;
        final File certs = getAbsoluteFilePathFromClassPath("crl/revoked.crt.pem");
        try(FileInputStream trin = new FileInputStream(certs)) {
            certsToValidate =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(certsToValidate.size(), 2);
        
        CertificateValidator validator = new CertificateValidator(rootCas.toArray(new X509Certificate[0]), Collections.EMPTY_LIST);
        try {
            validator.validate(certsToValidate.toArray(new X509Certificate[0]));
            Assert.fail();
        } catch (CertificateException e) {
            Assert.assertTrue(e.getCause() instanceof CertPathBuilderException);
            Assert.assertTrue(e.getCause().getMessage().contains("unable to find valid certification path to requested target"));
        }
    }
    
    @Test
    public void testCRLDP() throws Exception {

        //trust chain incl intermediate certificates (root + intermediates)
        Collection<? extends Certificate> rootCas;
        final File trustedCas = getAbsoluteFilePathFromClassPath("root-ca.pem");
        try(FileInputStream trin = new FileInputStream(trustedCas)) {
            rootCas =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(rootCas.size(), 1);

        //certificate chain to validate (client cert + intermediates but without root)
        Collection<? extends Certificate> certsToValidate;
        final File certs = getAbsoluteFilePathFromClassPath("crl/revoked.crt.pem");
        //final File certs = getAbsoluteFilePathFromClassPath("node-0.crt.pem");
        try(FileInputStream trin = new FileInputStream(certs)) {
            certsToValidate =  (Collection<? extends Certificate>) CertificateFactory.getInstance("X.509").generateCertificates(trin);
        }
        
        Assert.assertEquals(certsToValidate.size(), 2);
        
        CertificateValidator validator = new CertificateValidator(rootCas.toArray(new X509Certificate[0]), Collections.EMPTY_LIST);
        validator.setEnableCRLDP(true);
        validator.setEnableOCSP(true);
        try {
            validator.validate(certsToValidate.toArray(new X509Certificate[0]));
            Assert.fail();
        } catch (CertificateException e) {
            Assert.assertTrue(e.getCause().getCause().getCause() instanceof CertificateRevokedException);
        }
    }

    public File getAbsoluteFilePathFromClassPath(final String fileNameFromClasspath) {
        File file = null;
        final URL fileUrl = AbstractUnitTest.class.getClassLoader().getResource(fileNameFromClasspath);
        if (fileUrl != null) {
            try {
                file = new File(URLDecoder.decode(fileUrl.getFile(), "UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                return null;
            }

            if (file.exists() && file.canRead()) {
                return file;
            } else {
                log.error("Cannot read from {}, maybe the file does not exists? ", file.getAbsolutePath());
            }

        } else {
            log.error("Failed to load " + fileNameFromClasspath);
        }
        return null;
    }
}
