package cn.cgywin.framework.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.cgywin.framework.servlet.annotation.Autowired;
import cn.cgywin.framework.servlet.annotation.Controller;
import cn.cgywin.framework.servlet.annotation.RequestMapping;
import cn.cgywin.framework.servlet.annotation.RequestParams;
import cn.cgywin.framework.servlet.annotation.Service;

public class DisptcherServlet extends HttpServlet{
	
	/**
	 * 
	 */
	private int port = 8080;
	private static final long serialVersionUID = 1L;
	private Properties properties  = new Properties();
	private List<String> classNames = new ArrayList<String>();
	private Map<String,Object> ioc = new HashMap<String,Object>();
	
	//private Map<String,Method> handlerMapping = new HashMap<String,Method>();
	List<Handler> handlerMapping = new ArrayList<Handler>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		 
		doDispatch(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		 
		doDispatch(req, resp);
	}
	
	public int getPort() {
		return this.port;
	}
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		
		//String configFile = config.getInitParameter("contextConfigLoaction");
		//加载配置文件
		//doLoadConfig(configFile);
		//String p = properties.getProperty("port");
		//port = Integer.parseInt(p);
		//doScanner(properties.getProperty("scanPackage"));
		doScanner("cn.cgywin.bty");
		
		doInstance();
		
		doAutowired();
		
		initHandlerMapping();
	}		
	
	private void doScanner(String packname) {
		
		System.out.println("find pack:"+packname);
		if( this.getClass().getClassLoader().getResource("/")==null) {
			
			System.out.println("jar path:"+this.getClass().getProtectionDomain().getCodeSource().getLocation().getFile());
			
			String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
			
			JarFile jf = null;
			try{
			    jf = new JarFile(path);   
			}catch(Exception ex){
			    System.out.println(ex.getMessage());   
			}
			Enumeration<?> enu = jf.entries();
			while(enu.hasMoreElements()){
			    JarEntry element = (JarEntry) enu.nextElement();
			    String name = element.getName();
			    if(name.toUpperCase().endsWith(".CLASS")){
			          
			        name=name.replace(".class", "").replaceAll("/", ".");
			        if(name.indexOf(packname)==0) {
			        		System.out.println(name);
			        		classNames.add(name);
			        }
			    }
			}
		}else {
			URL url = this.getClass().getClassLoader().getResource("/"+packname.replaceAll("\\.", "/"));
			
			System.out.println("find class dir:"+url);
			File dir = new File(url.getFile());
			for(File file:dir.listFiles()) {
				
				if(file.isDirectory()) {
					doScanner(packname+"." +file.getName());
				}else {
					
					classNames.add(packname+"."+file.getName().replace(".class", ""));
				}
			}
		}
	}
   
   private void doLoadConfig(String configFile) {
		
		System.out.println("configFile:"+configFile);
		System.out.println(this.getClass().getClassLoader().getResource("/"));
		InputStream is =this.getClass().getClassLoader().getResourceAsStream(configFile);
		try {
			properties.load(is);
		} catch (IOException e) {
			
			e.printStackTrace();
		}finally{
			try {
				is.close();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		}
	}

	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		resp.setCharacterEncoding("UTF-8");
		String url  = req.getRequestURI();
		String context = req.getContextPath();
		url = url.replace(context, "").replaceAll("/+", "/");
		System.out.println("request url:"+url);
		Handler handler = getHandler(url);
		if(handler ==null) {
			
			 System.out.println("404 not found, url:"+url);
			 resp.getWriter().write("404 not found");
			 return;
		}
		
		System.out.println("handler mehod:"+handler.method.getName());
		//获取方法的参数列表
		Class<?> [] paramTypes = handler.method.getParameterTypes();
		//保存所有需要自动赋值的参数值
		Object[] paramValues = new  Object[paramTypes.length];
		Map<String,String[]> params = req.getParameterMap();
		for(Entry<String,String[]> param : params.entrySet()) {
			
			//System.out.println("method param :"+ Arrays.toString(param.getValue()));
			String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
			
			//匹配上对象，填充参数
			if(!handler.paramIndexMapping.containsKey(param.getKey())) {continue;}
						
			int index = handler.paramIndexMapping.get(param.getKey());
			paramValues[index]  = handler.convert(paramTypes[index],value);
			
			System.out.println("method param :["+index+"]"+value);
		}
			
		int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
		paramValues[reqIndex] = req;
		int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
		paramValues[respIndex] = resp;
		
		try {
			handler.method.invoke(handler.controller, paramValues);
		} catch (Exception e) {
	
			e.printStackTrace();
		}
		
	}
	
	
	private Handler getHandler(String url) {
		
		if(handlerMapping.isEmpty()) {return null;}
		
		for(Handler handler:handlerMapping) {
			
			try{
				
				Matcher matcher  =handler.pattern.matcher(url);
				if(!matcher.matches()){continue;}
				return handler;
			}catch(Exception e) {
				e.getStackTrace();
			}
		}
		return null;
	}
	

	private void initHandlerMapping() {
		
		if(ioc.isEmpty()) {return;}
		
		System.out.println("initHandlerMapping:@RequestMapping to method");
		for(Entry<String,Object> entry:ioc.entrySet()) {
			
			Class<?> clazz = entry.getValue().getClass();
			
			if(!clazz.isAnnotationPresent(Controller.class)) {continue;};
			
			String baseUrl="";
			if(clazz.isAnnotationPresent(RequestMapping.class)) {
				
				RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
				baseUrl = requestMapping.value();
				//System.out.println("baseUrl:"+baseUrl);
			}
			
			Method[] methods = clazz.getMethods();
			
			for(Method method:methods) {
				if(!method.isAnnotationPresent(RequestMapping.class)) {continue;};
				
				RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
				String mappingUrl =( "/"+baseUrl+"/"+ requestMapping.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(mappingUrl);
				handlerMapping.add(new Handler(pattern, entry.getValue(),method));
				System.out.println("add handlerMapping:"+mappingUrl+"->"+method);
			}
		}
		
		
	}

	private void doAutowired() {
		
		if(ioc.isEmpty()) {return;}
		
		System.out.println("doAutowired:");
		for(Entry<String,Object> entry:ioc.entrySet()) {
			
			Field [] fields = entry.getValue().getClass().getDeclaredFields();
			
			for(Field field:fields) {
				
				if(!field.isAnnotationPresent(Autowired.class)) {continue;}
				
				Autowired autowired = field.getAnnotation(Autowired.class);
				String beanName = autowired.value().trim();
				if(beanName.equals("")) {
					
					beanName = field.getType().getName();
					
					System.out.println("find Autowired:"+beanName);
				}
				//为true,则表示可以忽略访问权限的限制，直接调用。
				field.setAccessible(true);
				try {
					field.set(entry.getValue(), ioc.get(beanName));
				}catch (Exception e) {
					
					e.printStackTrace();
				}
			}
		}
		
	}

	private void doInstance() {
		
		if(classNames.isEmpty()) {return;};
		try {
			
			for(String classname:classNames) {
				
				Class<?> clazz = Class.forName(classname);
				
				if(clazz.isAnnotationPresent(Controller.class)) {
					
					String beanName = lowerFirest(clazz.getSimpleName());
					ioc.put(beanName, clazz.newInstance());
					
					System.out.println("add ioc bean Controller:"+beanName);
				}else if(clazz.isAnnotationPresent(Service.class)) {
					//1. 注解中自定义了名字 ： @Service("demoService")
					//2. 没有定义名字，使用首字母小写
					//3. 注入的是接口，找到实现的类实例注入
					
					Service service = clazz.getAnnotation(Service.class);
					String beanName = service.value();
					if(!beanName.trim().equals("")) {//如果设置了名字
						ioc.put(beanName, clazz.newInstance());
						System.out.println("add ioc bean Service:"+beanName);
					}else {
						beanName = lowerFirest(clazz.getSimpleName());
						ioc.put(beanName, clazz.newInstance());
						System.out.println("add ioc bean Service:"+beanName);
					}
					
					Class<?>[] interfaces = clazz.getInterfaces();
					
					for(Class<?> i:interfaces) {
						beanName =i.getName();
						ioc.put(beanName, clazz.newInstance());
						System.out.println("add ioc bean Interface:"+beanName);
					}
				}else {continue;}
			}
		} catch (Exception e) {
			
			e.printStackTrace();
		}
		
	}

	
	

	/**
	 * 首字母转换成小写
	 * @param str
	 * @return
	 */
	private String lowerFirest(String str) {
		
		char [] cs = str.toCharArray();
		cs[0]+=32;
		return String.valueOf(cs);
	}
	
	private class Handler {
		
		protected  Object controller;
		protected  Method method;
		protected  Pattern pattern;
		protected Map<String,Integer> paramIndexMapping;
		
		protected Handler(Pattern pattern,Object controller,Method method) {
			
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;
			this.paramIndexMapping = new HashMap<String,Integer>();
			putParamIndexMapping(method);
		}

		private void putParamIndexMapping(Method method2) {
			
			Annotation [][] pa = method.getParameterAnnotations();
			for(int i = 0; i<pa.length;i++) {
				
				for(Annotation a: pa[i]) {
					
					if(a instanceof RequestParams) {
						String paramName = ((RequestParams)a).value();
						if(!paramName.trim().equals("")) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			Class<?> [] paramsTypes = method.getParameterTypes();
			for(int i = 0; i<paramsTypes.length;i++) {
				
				Class<?> type = paramsTypes[i];
				if(type==HttpServletRequest.class || type==HttpServletResponse.class ) {
					
					paramIndexMapping.put(type.getName(), i);
				}
			}
			
		}
		
		private Object convert(Class<?> type,String value) {
			
			if(Integer.class == type) {
				return Integer.valueOf(value);
			}
			
			return value;
		}
	}

}
