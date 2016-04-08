import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.script.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MockHelper {

	private static MockHelper ins;
	
	private NashornScriptEngine nashorn;

    private CompiledScript compiledScript;

	Logger log = LoggerFactory.getLogger(MockHelper.class);

	static public MockHelper getInstance(){
		if(ins == null)
			ins = new MockHelper();
		return ins;
	}
	
	private MockHelper(){
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		nashorn = (NashornScriptEngine) scriptEngineManager.getEngineByName("nashorn");
		try {
			//inited
			nashorn.eval("var yod = load('classpath:yod-mock.js')");
//          compiledScript = nashorn.compile("load('classpath:yod-mock.js')");
//			nashorn.eval(new FileReader(MockHelper.class.getClassLoader().getResource("mock.js").getFile()));
		} catch (ScriptException e) {
			e.printStackTrace();
		}
	}
	
	public void loadTemplates(Reader templateReader){
		try {
			nashorn.eval(templateReader);
		} catch (ScriptException e) {
			e.printStackTrace();
		}
	}

	/**
	 * TODO
	 * @param script
	 * @return
	 */
	public synchronized Object eval(String script){
		try {
//            return ((ScriptObjectMirror)compiledScript.eval()).call("", script).toString();
            return nashorn.eval("yod('" + script + "') + '';").toString();
		} catch (Exception e) {
			log.error("script: " + script + ", reason:" + e.getMessage());
		}
		return null;
	}
	
	public <T> List<T> genCollection(Class<?> type, String typeName, Integer limit){
		List<T> result = null;
		try{
			nashorn.eval("var result = yod({list:'@"+typeName+".repeat("+limit+")'}).list;");
	        Object value = nashorn.eval("JSON.stringify(result)");
	        ObjectMapper mapper = new ObjectMapper();
	        JavaType javaType =  new ObjectMapper().getTypeFactory().constructParametrizedType(ArrayList.class, List.class, type); 
	        ObjectReader reader = mapper.reader().forType(javaType);
	        result = reader.readValue(value.toString());
		}catch(Exception ex){
			ex.printStackTrace();
		}
        return result;
	}
	
	public <T> List<T> genCollection(Class<T> type, Integer limit){
		String typeName = type.getSimpleName();
		return genCollection(type, typeName, limit);
	}
	
	
}
