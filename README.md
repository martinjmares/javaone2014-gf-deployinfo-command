javaone2014-gf-deployinfo-command
=================================

It is demonstration command for JavaOne 2014. GlassFish supplemental admin command that sends mail when application is deployed.

This command uses simple rest application deployed on [CloudBees](http://www.cloudbees.com/) infrastructure and dedicated [GMail](http://gamil.com) account. Both are considered as private and personal for Martin Mares and can be non-existing or not functional in time when you want to use this code. And public sources does not contains credentials for these services.

Main porpouse of this code is to be used as an example. It means that personal accounts used in this application should be replaced with other services or accounts.

What is it
----------

This is example of two supplemental command for deploy command. If it is installed on GlassFish 4.1 then part will be executed before each deployment and part after. Before part will download mail addresses from defined REST resource and post part will send notification e-mails on these addresses.

Build and install
-----------------

**_This command sources must be first customize with your resource or SMTP account!_** 

**Build:** Use maven and Java 7
       mvn clean install

**Deployment:** Copy created jar file to _module_ directory in your GlassFish 4.1 installation
