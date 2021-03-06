/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.lists.form.web.internal.portlet;

import com.liferay.dynamic.data.lists.form.web.internal.constants.DDLFormPortletKeys;
import com.liferay.dynamic.data.lists.form.web.internal.display.context.DDLFormDisplayContext;
import com.liferay.dynamic.data.lists.model.DDLRecordSet;
import com.liferay.dynamic.data.lists.model.DDLRecordSetSettings;
import com.liferay.dynamic.data.lists.service.DDLRecordSetService;
import com.liferay.dynamic.data.lists.service.DDLRecordVersionLocalService;
import com.liferay.dynamic.data.mapping.form.renderer.DDMFormRenderer;
import com.liferay.dynamic.data.mapping.form.values.factory.DDMFormValuesFactory;
import com.liferay.dynamic.data.mapping.util.DDMFormValuesMerger;
import com.liferay.dynamic.data.mapping.validator.DDMFormValuesValidationException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.service.WorkflowDefinitionLinkLocalService;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.IOException;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.PortletSession;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Marcellus Tavares
 */
@Component(
	immediate = true,
	property = {
		"com.liferay.portlet.add-default-resource=true",
		"com.liferay.portlet.application-type=full-page-application",
		"com.liferay.portlet.application-type=widget",
		"com.liferay.portlet.css-class-wrapper=portlet-forms-display",
		"com.liferay.portlet.display-category=category.collaboration",
		"com.liferay.portlet.friendly-url-mapping=form",
		"com.liferay.portlet.header-portlet-css=/admin/css/main.css",
		"com.liferay.portlet.instanceable=true",
		"com.liferay.portlet.preferences-owned-by-group=true",
		"com.liferay.portlet.private-request-attributes=false",
		"com.liferay.portlet.render-weight=50",
		"com.liferay.portlet.scopeable=true",
		"com.liferay.portlet.use-default-template=true",
		"javax.portlet.display-name=Dynamic Data Lists Form",
		"javax.portlet.expiration-cache=0",
		"javax.portlet.init-param.copy-request-parameters=true",
		"javax.portlet.init-param.template-path=/display/",
		"javax.portlet.init-param.view-template=/display/view.jsp",
		"javax.portlet.name=" + DDLFormPortletKeys.DYNAMIC_DATA_LISTS_FORM,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=guest,power-user,user",
		"javax.portlet.supports.mime-type=text/html"
	},
	service = Portlet.class
)
public class DDLFormPortlet extends MVCPortlet {

	@Override
	public void processAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws IOException, PortletException {

		try {
			super.processAction(actionRequest, actionResponse);
		}
		catch (Exception e) {
			_portal.copyRequestParameters(actionRequest, actionResponse);

			Throwable cause = getRootCause(e);

			hideDefaultErrorMessage(actionRequest);

			if (cause instanceof DDMFormValuesValidationException) {
				if (cause instanceof
						DDMFormValuesValidationException.MustSetValidValues ||
					cause instanceof
						DDMFormValuesValidationException.RequiredValue) {

					SessionErrors.add(actionRequest, cause.getClass(), cause);
				}
				else {
					SessionErrors.add(
						actionRequest, DDMFormValuesValidationException.class);
				}
			}
			else {
				SessionErrors.add(actionRequest, cause.getClass(), cause);
			}

			if (isSharedLayout(actionRequest)) {
				saveParametersInSession(actionRequest);
			}
		}
	}

	@Override
	public void render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		try {
			setRenderRequestAttributes(renderRequest, renderResponse);

			DDLFormDisplayContext ddlFormPortletDisplayContext =
				(DDLFormDisplayContext)renderRequest.getAttribute(
					WebKeys.PORTLET_DISPLAY_CONTEXT);

			checkFormIsNotRestricted(
				renderRequest, renderResponse, ddlFormPortletDisplayContext);
		}
		catch (Exception e) {
			if (isSessionErrorException(e)) {
				if (_log.isWarnEnabled()) {
					_log.warn(e, e);
				}

				hideDefaultErrorMessage(renderRequest);

				SessionErrors.add(renderRequest, e.getClass());
			}
			else {
				throw new PortletException(e);
			}
		}

		super.render(renderRequest, renderResponse);
	}

	protected void checkFormIsNotRestricted(
			RenderRequest renderRequest, RenderResponse renderResponse,
			DDLFormDisplayContext ddlFormDisplayContext)
		throws PortalException {

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		DDLRecordSet recordSet = ddlFormDisplayContext.getRecordSet();

		if (recordSet == null) {
			return;
		}

		DDLRecordSetSettings recordSetSettings = recordSet.getSettingsModel();

		Layout layout = themeDisplay.getLayout();

		if (recordSetSettings.requireAuthentication() &&
			!layout.isPrivateLayout()) {

			throw new PrincipalException.MustBeAuthenticated(
				themeDisplay.getUserId());
		}
	}

	protected Throwable getRootCause(Throwable throwable) {
		while (throwable.getCause() != null) {
			throwable = throwable.getCause();
		}

		return throwable;
	}

	@Override
	protected boolean isSessionErrorException(Throwable cause) {
		return false;
	}

	protected boolean isSharedLayout(ActionRequest actionRequest) {
		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		Layout layout = themeDisplay.getLayout();

		String layoutFriendlyURL = layout.getFriendlyURL();

		if (layoutFriendlyURL.equals("/shared")) {
			Group group = themeDisplay.getSiteGroup();

			String groupFriendlyURL = group.getFriendlyURL();

			if (groupFriendlyURL.equals("/forms")) {
				return true;
			}
		}

		return false;
	}

	protected void saveParametersInSession(ActionRequest actionRequest) {
		long recordSetId = ParamUtil.getLong(actionRequest, "recordSetId");

		if (recordSetId > 0) {
			PortletSession portletSession = actionRequest.getPortletSession();

			portletSession.setAttribute("recordSetId", recordSetId);
			portletSession.setAttribute("shared", Boolean.TRUE);
		}
	}

	protected void setRenderRequestAttributes(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws PortalException {

		DDLFormDisplayContext ddlFormDisplayContext = new DDLFormDisplayContext(
			renderRequest, renderResponse, _ddlRecordSetService,
			_ddlRecordVersionLocalService, _ddmFormRenderer,
			_ddmFormValuesFactory, _ddmFormValuesMerger,
			_workflowDefinitionLinkLocalService);

		renderRequest.setAttribute(
			WebKeys.PORTLET_DISPLAY_CONTEXT, ddlFormDisplayContext);
	}

	private static final Log _log = LogFactoryUtil.getLog(DDLFormPortlet.class);

	@Reference
	private DDLRecordSetService _ddlRecordSetService;

	@Reference
	private DDLRecordVersionLocalService _ddlRecordVersionLocalService;

	@Reference
	private DDMFormRenderer _ddmFormRenderer;

	@Reference
	private DDMFormValuesFactory _ddmFormValuesFactory;

	@Reference
	private DDMFormValuesMerger _ddmFormValuesMerger;

	@Reference
	private Portal _portal;

	@Reference
	private WorkflowDefinitionLinkLocalService
		_workflowDefinitionLinkLocalService;

}