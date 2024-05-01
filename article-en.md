
Kotlin + Maven toolchain

Main article idea is to show how to make IT (kotlin & maven toolchain) work together.
There will not be detailed description of Maven toolchain, but there will be references to official documentation.

Prelude.
 As kotlin developer I am absolutely not interested in new java versions,
 but one yummy appeared in JDK 22 - panama/foreign was released (earlier it was in incubating phase).
 For those who are not in the know, panama/foreign allows to call native code (from your java code without additional third-parties).
 
Maven toolchain in short
It allows you to pick required jdk up (or other tool) automatically.
 Until 2024 maven toolchain plugin was very simple/weak (comparing with gradle toolchains)
 - only toolchain/jdk from $HOME/.m2/toolchains.xml were supported

But recently (in April 2024) new version appeared and it supports
 * $home/.m2/toolchains.xml (as previous one)
 * can use current JDK ($JAVA_HOME) if it matches specified requirements
 * does search in predefined system directories (for example, C:/Program Files/...)
 * does search in environment variables by pattern (for example: JAVA11_HOME, JAVA22_HOME). The pattern is configurable.
 * [custom toolchains](https://maven.apache.org/plugins/maven-toolchains-plugin/toolchains/custom.html)

At that moment maven toolchain is a bit better than even its sibling from a gradle world.
 Gradle has bug about ignoring 'vendor' (and other attributes) from $home/.m2/toolchains.xml,
 as result it is impossible to distinguish Oracle (standard) JDK and Oracle Graal JDK.


Let's move to main our idea.

We still have one problem - maven kotlin plugin does not make 'friendship' with maven toolchain plugin.
 At least I didn't find way how to force kotlin to use toolchain JDK. 

However... maven kotlin plugin has a configuration parameter 'jdkHome', which is mapped to maven property 'toolchain.jdk.version'.
 It will save us - we just need to get JDK home from toolchain plugin and set corresponding maven property.
 The solution is less-more reliable (all risky code is put into try/catch).
 It is your choice to use it in production, or use only in pet projects.
 In the worst case, it just wil not work, and you will need to set up JAVA_HOME (as did it usually).


```xml
<project>
    ...
    <properties>
        <!-- Required JDK version in maven-artifact format. -->
        <toolchain.jdk.version>[22,)</toolchain.jdk.version>

        <!-- Required java-version in numeric format. -->
        <java.version>22</java.version>
        <java.release>${java.version}</java.release>
        <kotlin.version>1.7.0</kotlin.version>
        <kotlin.compiler.languageVersion>1.7</kotlin.compiler.languageVersion>

        <kotlin.compiler.jvmTarget>15</kotlin.compiler.jvmTarget>
        <kotlin.compiler.incremental>true</kotlin.compiler.incremental>

        <kotlin-maven-plugin.version>${kotlin.version}</kotlin-maven-plugin.version>
    </properties>

    <build>
        <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-toolchains-plugin</artifactId>
              <version>3.2.0</version>
              <executions>
                <execution>
                  <goals>
                    <goal>select-jdk-toolchain</goal>
                  </goals>
                </execution>
              </executions>
              <configuration>
                <version>${toolchain.jdk.version}</version>
                <!-- Optional old config. Let's keep it to avoid IDEA warning. -->
                <toolchains>
                  <jdk>
                    <version>${toolchain.jdk.version}</version>
                  </jdk>
                </toolchains>
              </configuration>
            </plugin>
            
            <plugin>
              <groupId>org.codehaus.mojo</groupId>
              <artifactId>build-helper-maven-plugin</artifactId>
              <version>3.4.0</version>
              <executions>
                <execution>
                  <id>use-maven-toolchain-jdk-for-kotlin</id>
                  <goals>
                    <goal>bsh-property</goal>
                  </goals>
                  <configuration>
                    <source><![CDATA[
                      import org.apache.maven.plugin.descriptor.PluginDescriptor;
            
                      String toolChainJdk = null;
            
                      try {
                        PluginDescriptor pd = new PluginDescriptor();
                        pd.setGroupId("org.apache.maven.plugins");
                        pd.setArtifactId("maven-toolchains-plugin");
            
                        Map pluginContext = session.getPluginContext(pd, project);
                        if (pluginContext == null)
                          throw new IllegalStateException("maven-toolchains-plugin plugin context is not found. Probably it is not set up.");
            
                        // A bit risky part of code: I'm not 100% sure that this class will be used in the future.
                        // It is not specified in the org.apache.maven.model.ConfigurationContainer
                        // (there is just java.lang.Object, but it must be some DOM)
            
                        Object toolchainPluginContext = pluginContext.get("toolchain-jdk");
                        if (toolchainPluginContext != null) {
                          // We can use type Object in bean-shell script instead of Xpp3Dom.
                          // Real type is XML DOM object (currently type is org.codehaus.plexus.util.xml.Xpp3Dom class)
            
                          Object config = toolchainPluginContext.getConfiguration();
                          if (config != null && config.getChildCount() > 0)
                            toolChainJdk = config.getChild(0).getValue().trim();
                        }
                      } catch (Exception ex) {
                        log.error("toolchain-jdk is not found. " + ex.getMessage(), ex);
            
                        // Or we can rethrow error just there.
                        // throw new IllegalStateException("toolchain-jdk is not found.", ex);
                      }
            
                      String requiredJdkVersion = project.getProperties().getProperty("toolchain.jdk.version");
                      if (toolChainJdk != null && !toolChainJdk.isEmpty()) {
                        project.getProperties().setProperty("kotlin.compiler.jdkHome", toolChainJdk);
            
                        log.info("toolchain-jdk for version '" + requiredJdkVersion + "' is " + toolChainJdk);
                        log.info("It will be used for kotlin compiler");
                      }
                      else {
                        String currentJavaHome = System.getProperty("java.home");
            
                        log.info("toolchain-jdk for version '" + requiredJdkVersion + "' is not found.");
                        log.info("  Possible reasons");
                        log.info("    * default java_home matches required java version ");
                        log.info("    * maven-toolchains-plugin is not configured properly");
                        log.info("  ");
                        log.info("Default " + currentJavaHome + " will be used.");
                      }
            
                      ]]>
                    </source>
                  </configuration>
                </execution>
              </executions>
            </plugin>
        
        </plugins>
    </build>
</project>
```

Sources [GitHub](https://github.com/odisseylm/kotlin-with-maven-toolchain)

Useful links
 * Maven Toolchains Plugin [home](https://maven.apache.org/plugins/maven-toolchains-plugin/)
   * [JDK Toolchain discovery mechanism](https://maven.apache.org/plugins/maven-toolchains-plugin/toolchains/jdk-discovery.html)
   * Goals
     * [toolchains:select-jdk-toolchain](https://maven.apache.org/plugins/maven-toolchains-plugin/select-jdk-toolchain-mojo.html)
     * [toolchains:display-discovered-jdk-toolchains](https://maven.apache.org/plugins/maven-toolchains-plugin/display-discovered-jdk-toolchains-mojo.html)
     * [toolchains:generate-jdk-toolchains-xml](https://maven.apache.org/plugins/maven-toolchains-plugin/generate-jdk-toolchains-xml-mojo.html)
     * [старенький слабенький toolchains:toolchain](https://maven.apache.org/plugins/maven-toolchains-plugin/toolchain-mojo.html)
     * [toolchains:help](https://maven.apache.org/plugins/maven-toolchains-plugin/help-mojo.html)
   * [Конфигурация](https://maven.apache.org/plugins-archives/maven-toolchains-plugin-LATEST/select-jdk-toolchain-mojo.html) (or SelectJdkToolchainMojo.java source)
   * On mojohaus
     * [Using Toolchains Instead of Explicit Paths](https://www.mojohaus.org/exec-maven-plugin/examples/example-exec-using-toolchains.html)
 * [SDKMAN](https://sdkman.io/)
