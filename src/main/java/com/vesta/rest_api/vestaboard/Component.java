package com.vesta.rest_api.vestaboard;

import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

public class Component {
	private String justify;
	private String align;
	private String body;
	private Integer height;

	public Component() {
		justify = "center";
		align = "center";
		height = 6;
	}

	public void setJustify(String style) {
		justify = style;
	}

	public String getJustify() {
		return justify;
	}

	public void setAlign(String style) {
		align = style;
	}

	public String getAlign() {
		return align;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getBody() {
		return body;
	}

	public void setHeight(Integer h) {
		height = h;
	}

	public Integer getHeight() {
		return height;
	}

	/**
	 * Builds a JSON request string for a component.
	 *
	 * This method constructs a JSON representation of a component with its style
	 * and template.
	 * It uses the Gson library to convert the component structure into a JSON
	 * string.
	 *
	 * @return A JSON string representing the component.
	 */
	private String buildRequest() {
		Gson gson = new Gson();

		HashMap<String, Object> component = new HashMap<>();

		HashMap<String, Object> style = new HashMap<>();
		style.put("justify", justify);
		style.put("align", align);

		HashMap<String, String> template = new HashMap<>();
		template.put("template", body);

		HashMap<String, Object>[] componentBody = new HashMap[1];
		componentBody[0] = new HashMap<>();
		componentBody[0].put("style", style);
		componentBody[0].put("template", body);

		component.put("components", componentBody);

		String json = gson.toJson(component);
		return json;
	}

	public static String compileComponents(Component... components) {
		Gson gson = new Gson();
		HashMap<String, Object> compiledComponents = new HashMap<>();
		HashMap<String, Object>[] componentArray = new HashMap[components.length];

		for (int i = 0; i < components.length; i++) {
			HashMap<String, Object> component = new HashMap<>();
			HashMap<String, Object> style = new HashMap<>();
			style.put("justify", components[i].getJustify());
			style.put("align", components[i].getAlign());
			style.put("height", components[i].getHeight());

			component.put("style", style);
			component.put("template", components[i].getBody());

			componentArray[i] = component;
		}

		compiledComponents.put("components", componentArray);
		String component = gson.toJson(compiledComponents);

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpPost request = new HttpPost("https://vbml.vestaboard.com/compose");
			request.setHeader("Content-Type", "application/json");
			final StringEntity requestBody = new StringEntity(component);
			request.setEntity(requestBody);

			try {
				HttpResponse response = client.execute(request);
				String responseString = EntityUtils.toString(response.getEntity());
				System.out.println(responseString);
				return responseString;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Creates a 2D Array for the component.
	 *
	 * This method submits the JSON from buildRequest() to Vestaboards VBML API,
	 * which returns a components 2D array.
	 *
	 * @return A JSON string representing the component.
	 */
	public String getVBML() {
		final String URL = "https://vbml.vestaboard.com/compose";

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpPost request = new HttpPost(URL);
			request.setHeader("Content-Type", "application/json");
			final String bodyString = buildRequest();
			final StringEntity requestBody = new StringEntity(bodyString);
			request.setEntity(requestBody);

			// Who decided needing to nest a try catch inside another try catch was a good
			// idea.
			try {
				HttpResponse response = client.execute(request);
				String result = EntityUtils.toString(response.getEntity());
				return result;
			} catch (Exception e) {
				e.printStackTrace();
				;
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}