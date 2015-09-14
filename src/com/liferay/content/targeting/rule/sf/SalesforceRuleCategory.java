package com.liferay.content.targeting.rule.sf;

import com.liferay.content.targeting.api.model.BaseRuleCategory;
import com.liferay.content.targeting.api.model.RuleCategory;

import org.osgi.service.component.annotations.Component;

@Component(immediate = true, service = RuleCategory.class)
public class SalesforceRuleCategory extends BaseRuleCategory
{
	public static final String KEY = "salesforce-attributes";

	@Override
	public String getCategoryKey() {
		return KEY;
	}

	@Override
	public String getIcon() {
		return "icon-cloud";
	}
}
