import com.couchbase.lite.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Test {
    public static void main(String[] args) throws Exception {
        File file = new File(System.getProperty("java.io.tmpdir"));

        System.out.println("Database Location : " + file.getAbsolutePath());

        Manager manager = new Manager(file, Manager.DEFAULT_OPTIONS);

        Database database = manager.getDatabase("food");

        View foodByType = database.getView("foodByType");
        foodByType.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (((String)document.get("id")).startsWith("food:")) {
                    System.out.println("  " + document.get("id"));
                    emitter.emit(new Object[] {document.get("type"), document.get("name")}, document);
                }
            }
        }, "1.0");

        System.out.println("Loading data:");
        // Load data.
        String[][] foods = {
                new String[] {"Fruit","Apple"},
                new String[] {"Fruit","Banana"},
                new String[] {"Vegetable","Carrot"},
                new String[] {"Vegetable","Potatoe"},
                new String[] {"Meat","Beef"},
                new String[] {"Meat","Chicken"},
                new String[] {"Fish","Cod"},
                new String[] {"Fish","Haddock"}
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

        System.out.println("Listing data:");

        Query query = foodByType.createQuery();
        QueryEnumerator enumerator = query.run();

        while (enumerator.hasNext()) {
            QueryRow row = enumerator.next();
            Document doc = row.getDocument();
            String type = (String) doc.getProperty("type");
            String name = (String) doc.getProperty("name");
            System.out.println("  " + type + ": " + name);
        }

        database.delete();
    }
}