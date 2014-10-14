package org.cqframework.cql.cql2js;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.cqframework.cql.cql2elm.Cql2ElmVisitor;
import org.cqframework.cql.cql2elm.preprocessor.CqlPreprocessorVisitor;
import org.cqframework.cql.gen.cqlLexer;
import org.cqframework.cql.gen.cqlParser;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.ObjectFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

public class CqlLibrary {

    private final String json;

    public static CqlLibrary loadCql(String cqlText) {
        return loadANTLRInputStream(new ANTLRInputStream(cqlText));
    }

    public static CqlLibrary loadCql(File cqlFile) throws IOException {
        return loadANTLRInputStream(new ANTLRInputStream(new FileInputStream(cqlFile)));
    }

    public static CqlLibrary loadElm(Library library) {
        try {
            return new CqlLibrary(library);
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Couldn't process ELM", e);
        }
    }

    private static CqlLibrary loadANTLRInputStream(ANTLRInputStream is) {
        try {
            return new CqlLibrary(is);
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Couldn't process CQL", e);
        }
    }

    private CqlLibrary(ANTLRInputStream is) throws JAXBException {
        this.json = convertToJSON(is);
    }

    private CqlLibrary(Library library) throws JAXBException {
        this.json = convertToJSON(library);
    }

    public String asJson() {
        return json;
    }

    public JsonNode asJsonNode() throws IOException {
        return new ObjectMapper().readTree(json);
    }

    private String convertToJSON(ANTLRInputStream is) throws JAXBException {
        cqlLexer lexer = new cqlLexer(is);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        cqlParser parser = new cqlParser(tokens);
        parser.setBuildParseTree(true);
        ParseTree tree = parser.logic();

        CqlPreprocessorVisitor preprocessor = new CqlPreprocessorVisitor();
        preprocessor.visit(tree);

        Cql2ElmVisitor visitor = new Cql2ElmVisitor();
        visitor.setLibraryInfo(preprocessor.getLibraryInfo());
        visitor.setTokenStream(tokens);
        visitor.visit(tree);

        return convertToJSON(visitor.getLibrary());
    }

    private String convertToJSON(Library library) throws JAXBException {
        JAXBContext jc = JAXBContext.newInstance(Library.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty("eclipselink.media-type", "application/json");

        StringWriter writer = new StringWriter();
        marshaller.marshal(new ObjectFactory().createLibrary(library), writer);
        return writer.getBuffer().toString();
    }

    public static void main(String[] args) throws IOException, JAXBException {
        if (args.length == 0) {
            System.err.println("Must provide file path as argument");
            System.exit(1);
        }

        CqlLibrary library = CqlLibrary.loadCql(new File(args[0]));
        System.out.println(library.asJson());
    }
}