package com.liferay.content.targeting.rule.sf;

import com.liferay.content.targeting.anonymous.users.model.AnonymousUser;
import com.liferay.content.targeting.api.model.BaseRule;
import com.liferay.content.targeting.api.model.Rule;
import com.liferay.content.targeting.model.RuleInstance;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.model.User;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(immediate = true, service = Rule.class)
public class SalesforceRule extends BaseRule
{
	private String _baseUri = null;
	private Header _oauthHeader = null;

	@Activate
	@Override
	public void activate()
	{
		super.activate();

		connect();
	}

	@Deactivate
	@Override
	public void deActivate()
	{
		super.deActivate();
	}

	@Override
	public boolean evaluate(HttpServletRequest request, RuleInstance ruleInstance,
			AnonymousUser anonymousUser) throws Exception
	{

		User user = anonymousUser.getUser();

		if (user == null)
		{
			return false;
		}

		String email = user.getEmailAddress();

		if (email.equals(""))
		{
			return false;
		}

		JSONObject jsonObj = new JSONObject(ruleInstance.getTypeSettings());

		String field = jsonObj.getString("field");
		String value = jsonObj.getString("value");

		String queryString = String
				.format("SELECT %s FROM Account WHERE Id IN (SELECT AccountId from Contact WHERE Email = '%s')",
						field, email);

		JSONArray records = sendQuery(queryString);

		return checkAny(records, field, value);
	}

	@Override
	public String getIcon()
	{
		return "icon-briefcase";
	}

	@Override
	public String getRuleCategoryKey()
	{
		return SalesforceRuleCategory.KEY;
	}

	@Override
	public String getSummary(RuleInstance ruleInstance, Locale locale)
	{
		String summary = "";

		try
		{
			JSONObject jsonObj = new JSONObject(ruleInstance.getTypeSettings());

			String field = jsonObj.getString("field");
			String value = jsonObj.getString("value");

			summary = LanguageUtil.format(locale, "salesforce-account-field-x-equals-x",
					new Object[]
					{ field, value });
		}
		catch (JSONException jse)
		{
			jse.printStackTrace();
		}

		return summary;
	}

	@Override
	public String processRule(PortletRequest request, PortletResponse response, String id,
			Map<String, String> values)
	{

		String field = values.get("field");
		String value = values.get("value");

		JSONObject jsonObj = new JSONObject();

		try
		{
			jsonObj.put("field", field);
			jsonObj.put("value", value);
		}
		catch (JSONException jse)
		{
			jse.printStackTrace();
		}

		return jsonObj.toString();
	}

	@Override
	protected void populateContext(RuleInstance ruleInstance, Map<String, Object> context,
			Map<String, String> values)
	{
		String field = "";
		String value = "";

		if (!values.isEmpty())
		{
			field = values.get("field");
			value = values.get("value");
		}
		else if (ruleInstance != null)
		{
			String typeSettings = ruleInstance.getTypeSettings();

			try
			{
				JSONObject jsonObj = new JSONObject(typeSettings);

				field = jsonObj.getString("field");
				value = jsonObj.getString("value");
			}
			catch (JSONException jse)
			{
				jse.printStackTrace();
			}
		}

		context.put("field", field);
		context.put("value", value);
	}

	private void connect()
	{
		try
		{
			String consumerKey = PropsUtil.get("salesforce.consumerKey");
			String consumerSecret = PropsUtil.get("salesforce.consumerSecret");
			String username = PropsUtil.get("salesforce.username");
			String password = PropsUtil.get("salesforce.password");
			String securityToken = PropsUtil.get("salesforce.securityToken");
			String apiVersion = PropsUtil.get("salesforce.apiVersion");

			HttpClient httpClient = HttpClientBuilder.create().build();

			String loginURL = "https://login.salesforce.com/services/oauth2/token";

			ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
			postParameters.add(new BasicNameValuePair("grant_type", "password"));
			postParameters.add(new BasicNameValuePair("client_id", consumerKey));
			postParameters.add(new BasicNameValuePair("client_secret", consumerSecret));
			postParameters.add(new BasicNameValuePair("username", username));
			postParameters.add(new BasicNameValuePair("password", password + securityToken));

			HttpPost httpPost = new HttpPost(loginURL);
			httpPost.setEntity(new UrlEncodedFormEntity(postParameters, "utf-8"));

			HttpResponse response = httpClient.execute(httpPost);

			if (response.getStatusLine().getStatusCode() == 200)
			{
				String getResult = EntityUtils.toString(response.getEntity());

				JSONObject jsonObject = (JSONObject) new JSONTokener(getResult).nextValue();
				String accessToken = jsonObject.getString("access_token");
				String instanceUrl = jsonObject.getString("instance_url");

				httpPost.releaseConnection();

				_baseUri = instanceUrl + "/services/data/v" + apiVersion;
				_oauthHeader = new BasicHeader("Authorization", "OAuth " + accessToken);
			}
			else
			{
				System.err.println("Salesforce error code "
						+ response.getStatusLine().getStatusCode());
				System.err.println(EntityUtils.toString(response.getEntity()));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public JSONArray sendQuery(String query)
	{
		JSONArray records = new JSONArray();

		try
		{
			HttpResponse response = makeRestCall(query);

			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode != 200)
			{
				// Try re-authenticating
				connect();
				response = makeRestCall(query);
			}

			if (statusCode == 200)
			{
				// Successful query
				String response_string = EntityUtils.toString(response.getEntity());

				JSONObject json = new JSONObject(response_string);
				records = json.getJSONArray("records");
			}
			else
			{
				System.err.println("Salesforce error code "
						+ response.getStatusLine().getStatusCode());
				System.err.println(EntityUtils.toString(response.getEntity()));
			}
		}
		catch (Exception e)
		{
			// In case of *any* exception, we'll work with an empty JSON array
			e.printStackTrace();
		}

		return records;
	}

	private HttpResponse makeRestCall(String query) throws ClientProtocolException, IOException
	{
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(500)
				.setSocketTimeout(500).build();
		HttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig)
				.build();

		String encodedQuery = URLEncoder.encode(query, "UTF-8");

		String uri = _baseUri + "/query?q=" + encodedQuery;

		HttpGet httpGet = new HttpGet(uri);
		httpGet.addHeader(_oauthHeader);

		return httpClient.execute(httpGet);
	}

	public boolean checkAny(JSONArray records, String field, String value)
	{
		for (int i = 0; i < records.length(); i++)
		{
			try
			{
				if (records.getJSONObject(i).getString(field).equals(value))
				{
					return true;
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		return false;
	}
}