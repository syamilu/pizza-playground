# SoapUI's linux-bin tarball ships without a JRE. Java 11, not 17: SoapUI 5.7.2 bundles Groovy 3.0.6,
# whose ASM cannot read Java 17 class files ("Unsupported class file major version 61" in every script).
FROM eclipse-temurin:11-jre
ARG SOAPUI_VERSION=5.7.2
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL https://dl.eviware.com/soapuios/${SOAPUI_VERSION}/SoapUI-${SOAPUI_VERSION}-linux-bin.tar.gz | tar -xz -C /opt \
 && mv /opt/SoapUI-${SOAPUI_VERSION} /opt/soapui
WORKDIR /opt/soapui
COPY gateway-mock-soapui-project.xml /project/gateway.xml
EXPOSE 8090
# No -b: the runner must keep its "press any key" wait loop, which is what keeps it alive (stdin stays empty).
CMD ["/opt/soapui/bin/mockservicerunner.sh", "-m", "PaymentGatewayMock", "-p", "8090", "/project/gateway.xml"]
