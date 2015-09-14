<#assign aui = PortletJspTagLibs["/META-INF/aui.tld"] />

<@aui["input"] label="field" name="field" type="text" value=field>
	<@aui["validator"] name="required" />
</@>
<@aui["input"] label="value" name="value" type="text" value=value>
	<@aui["validator"] name="required" />
</@>