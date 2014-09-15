/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.javaone.deployinfo;

import com.sun.enterprise.util.StringUtils;
import org.glassfish.javaone.deployinfo.internal.InternalConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.*;
import org.glassfish.api.deployment.DeployCommandParameters;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Supplemental command for <i>deploy</i> command which sends information e-mail to all addresses previously acquired
 * by {@link org.glassfish.javaone.deployinfo.DeployInfoMailPostCommand}.
 * <p>
 * Secondary function is that e-mail message contains two part code which first part is unique within all sanded
 * messages. This code can be used to some game.
 * </p>
 * <p>
 *     <b>This is demonstration code for JavaOne 2014.</b>
 * </p>
 *
 * @author martin.mares(at)oracle.com
 */
@Service(name = "_deployinfomail-post-cmd")
@Supplemental(value="deploy", ifFailure= FailurePolicy.Warn, on = Supplemental.Timing.After)
@PerLookup
public class DeployInfoMailPostCommand extends DeployCommandParameters implements AdminCommand {

    private static final Logger logger = Logger.getLogger(DeployInfoMailPostCommand.class.getName());
    private static final String PUBLIC_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final Set<Integer> usedCodes = new HashSet<>();

    @Override
    public void execute(AdminCommandContext context) {
        logger.fine("execute()");
        ActionReport report = context.getActionReport();
        Properties props = report.getExtraProperties();
        if (props == null) {
            logger.warning("No addresses from DeployInfoMailPreCommand in context!");
            return;
        }
        Collection<String> addrs = (Collection<String>) props.get(DeployInfoMailPreCommand.KEY_EMAIL_ADDRESSES);
        if (addrs == null) {
            logger.warning("No addresses from DeployInfoMailPreCommand in context properties!");
            return;
        } else {
            //Prone it before continue
            props.remove(DeployInfoMailPreCommand.KEY_EMAIL_ADDRESSES);
        }
        //Find appName
        StringBuilder appNameSB = new StringBuilder();
        if (path != null) {
            appNameSB.append(path.getName()).append(' ');
        }
        if (StringUtils.ok(name)) {
            appNameSB.append('[').append(name).append(']');
        }
        if (appNameSB.length() == 0) {
            appNameSB.append("UNKNOWN");
        }
        String appName = appNameSB.toString();
        //Send mail
        sendMail(addrs, appName);
        //Append info to result
        ActionReport r = report.addSubActionsReport();
        r.appendMessage("Info was send to \n");
        for (String addr : addrs) {
            r.appendMessage("    ");
            r.appendMessage(anonymizeAddress(addr));
            r.appendMessage("\n");
        }
    }

    private int generatePublicCode() {
        int result = (int) (Math.random() * 799);
        result += 100;
        while (usedCodes.contains(result)) {
            result++;
        }
        usedCodes.add(result);
        return result;
    }

    private String generatePrivateCode() {
        StringBuilder result = new StringBuilder(3);
        for (int i = 0; i < 3; i++) {
            int ind = (int) (Math.random() * PUBLIC_CODE_CHARS.length());
            result.append(PUBLIC_CODE_CHARS.charAt(ind));
        }
        return result.toString();
    }

    private void sendMail(Collection<String> addresses, String applicationName) {
        logger.fine("sendMail(recipientSize = " + addresses.size() + ", " + applicationName + ")");
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("martin.glassfish@gmail.com", InternalConstants.MAIL_PASSWORD);
                    }
                });

        logger.fine("smtp connected");
        int countInvalid = 0;
        StringBuilder codeLog = new StringBuilder();
        for (String addr : addresses) {
            String privateCode = generatePrivateCode();
            int publicCode = generatePublicCode();
            codeLog.append(publicCode).append(" - ").append(privateCode).append('\n');
            String msg = String.format("Hi,\nApplication %s was deployed on GlassFish.\n" +
                    "Please remember this personal code %s - %s.\n\n" +
                    "Thank you for your participation on this experiment and enjoy JavaOne 2014.\n" +
                    "Martin", applicationName, publicCode, privateCode);
            String anonAddr = anonymizeAddress(addr);
            logger.info("Sending deploy info to " + anonAddr);
            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress("martin.glassfish@gmail.com"));
                message.setRecipients(Message.RecipientType.TO,
                        InternetAddress.parse(addr));
                message.setSubject("JavaOne 2014 GlassFish experiment");
                message.setText(msg);
                Transport.send(message);
            } catch (MessagingException e) {
                countInvalid++;
                logger.log(Level.WARNING, "Can not send message to " + anonAddr + ".", e);
            }
        }
        //File with codes
        logger.fine("store file for winner selecting");
        File f = new File(System.getProperty("user.home"));
        f = new File(f, "SELECTHERE.TXT");
        try (FileOutputStream fos = new FileOutputStream(f);) {
            fos.write(codeLog.toString().getBytes());
            logger.info("File with codes is seved to " + f.getPath());
        } catch (Exception exc) {
            logger.log(Level.WARNING, "Can not store file with codes", exc);
        }
    }

    private static String anonymizeAddress(String addr) {
        if (!StringUtils.ok(addr)) {
            return "";
        }
        int ind = addr.lastIndexOf('.');
        String sfx = "";
        if (ind >= 0) {
            sfx = addr.substring(ind);
            addr = addr.substring(0, ind);
        }
        StringBuilder result = new StringBuilder(addr.length() + 5);
        for (char ch : addr.toCharArray()) {
            if (result.length() == 0 || !Character.isLetterOrDigit(ch)) {
                result.append(ch);
            } else {
                result.append('\u00B7');
            }
        }
        result.append(sfx);
        return result.toString();
    }

}
