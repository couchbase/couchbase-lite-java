/**
 * Created by pasin on 3/14/14.
 */

import com.couchbase.lite.*;
import junit.framework.TestCase;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


public class BasicTest extends TestCase {

    public void testBasicFunctions() throws Exception {
        File file = new File(System.getProperty("java.io.tmpdir"));

        Manager manager = new Manager(file, Manager.DEFAULT_OPTIONS);
        assertNotNull("Cannot create manager.", manager);

        Database database = manager.getDatabase("food");
        assertNotNull("Cannot create database.", database);

        View foodByType = database.getView("foodByType");
        assertNotNull("Cannot create view.", foodByType);

        foodByType.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (((String)document.get("id")).startsWith("food:")) {
                    System.out.println("  " + document.get("id"));
                    emitter.emit(new Object[] {document.get("type"), document.get("name")}, document);
                }
            }
        }, "1.0");

        // Load data.
        String[][] foods = {
                new String[] {"Fruit","Apple"},
                new String[] {"Fruit","Banana"}
        };
        for (int i=0; i<foods.length; i++) {
            String[] food = foods[i];
            String id = "food:" + food[1];
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("id", id);
            properties.put("type", food[0]);
            properties.put("name", food[1]);

            Document document = database.createDocument();
            document.putProperties(properties);
        }

        // List Data
        Query query = foodByType.createQuery();
        assertNotNull("Cannot create Query.", query);

        QueryEnumerator enumerator = query.run();
        assertNotNull("Cannot query data.", enumerator);
        assertEquals("Not all documents were put into the database.", 2, enumerator.getCount());

        while (enumerator.hasNext()) {
            QueryRow row = enumerator.next();
            assertNotNull("Cannot get a query row from query result enumerator", row);

            Document doc = row.getDocument();
            assertNotNull("Cannot get a document from query result enumerator", doc);

            String type = (String) doc.getProperty("type");
            String name = (String) doc.getProperty("name");

            assertNotNull("Cannot get the document property named 'type'.", type);
            assertNotNull("Cannot get the document property named 'name'.", name);
        }

        database.delete();
    }
}
