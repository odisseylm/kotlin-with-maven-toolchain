
Kotlin + Maven toolchain

Главная идея статьи - это показать как заставить ЭТО (kotlin & maven toolchain) работать вместе.
Детального описания Maven toolchain здесь не будет, не хочу заниматься банальным переводом руководств.

Начну с прелюдии.
 Как котлинисту мне новые версии java как-то по боку, но тут в JDK 22 подъехала годната - panama/foreign вышла из инкубатора.
 Для тех кто не в теме, эта фича дает вам возможность вызывать нативный код из dll (прямо из java кода).
 Теперь вы можете вызывать системные функции сами, а не подключать неизвестные вам библиотеки.

Вкратце о Maven toolchain.
Эта фича позволяет подключать нужную версию jdk (или других инструментов) автоматически.
 До апреля 2024 года maven toolchain плагин был довольно слабенький (по сравнению с gradle toolchains)
 - он позволял выбирать toolchain/jdk только из $HOME/.m2/toolchains.xml.
Но вот недавно (в апреле 2024) подъехала новая версия, которая поддерживает
 * $home/.m2/toolchains.xml файл
 * может подхватывать текущий JDK ($JAVA_HOME), если он удовлетворяет заданным критериям
 * делает поиск в стандартных директориях (например, C:/Program Files/...) (TODO: пока лично не тестировал)
 * делает поиск в переменных окружения по паттерну (например: JAVA11_HOME, JAVA22_HOME). Паттерн конфигурируем.
 * [custom toolchains](https://maven.apache.org/plugins/maven-toolchains-plugin/toolchains/custom.html)

Сейчас maven toolchain даже немного лучше своего собрата из gradle.
 В gradle есть неприятный баг по игнорированию vendor (и др атрибутов) из $home/.m2/toolchains.xml,
 в результате невозможно отличить Oracle (standard) JDK от Oracle Graal JDK.


Перейдем к главному.

У нас всё ещё есть одна проблемка - maven kotlin plugin не дружит с maven toolchain plugin.
 По крайней мере я не нашел как ему сказать, чтобы он подружился. 

Но... у maven kotlin plugin есть конфигурационный параметр jdkHome, который мапится на maven property "toolchain.jdk.version".
 Это и будет нашим спасением - нужно взять JDK home, найденный toolchain plugin и установить его в соответсвующее свойство.
 Как по мне решение +- надежное (весь рискованный код помещен в try/catch), но это уже ваш выбор использовать ли его в production,
 или только в домашнем проекте. В худшем случае, оно просто не будет работать и вы просто вернетесь к старой доброй установке JAVA_HOME.


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

Полные исходники на [github](https://github.com/odisseylm/kotlin-with-maven-toolchain)

Полезные ссылки
* Maven Toolchains Plugin [home](https://maven.apache.org/plugins/maven-toolchains-plugin/)
    * [JDK Toolchain discovery mechanism](https://maven.apache.org/plugins/maven-toolchains-plugin/toolchains/jdk-discovery.html)
    * Goals
        * [toolchains:select-jdk-toolchain](https://maven.apache.org/plugins/maven-toolchains-plugin/select-jdk-toolchain-mojo.html)
        * [toolchains:display-discovered-jdk-toolchains](https://maven.apache.org/plugins/maven-toolchains-plugin/display-discovered-jdk-toolchains-mojo.html)
        * [toolchains:generate-jdk-toolchains-xml](https://maven.apache.org/plugins/maven-toolchains-plugin/generate-jdk-toolchains-xml-mojo.html)
        * [старенький слабенький toolchains:toolchain](https://maven.apache.org/plugins/maven-toolchains-plugin/toolchain-mojo.html)
        * [toolchains:help](https://maven.apache.org/plugins/maven-toolchains-plugin/help-mojo.html)
    * [Конфигурация](https://maven.apache.org/plugins-archives/maven-toolchains-plugin-LATEST/select-jdk-toolchain-mojo.html) (или сырцы SelectJdkToolchainMojo)
