package com.github.ka4ok85.wca.command;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.github.ka4ok85.wca.constants.Visibility;
import com.github.ka4ok85.wca.exceptions.BadApiResultException;
import com.github.ka4ok85.wca.exceptions.EngageApiException;
import com.github.ka4ok85.wca.exceptions.FailedGetAccessTokenException;
import com.github.ka4ok85.wca.exceptions.FaultApiResultException;
import com.github.ka4ok85.wca.exceptions.JobBadStateException;
import com.github.ka4ok85.wca.options.GetMailingTemplatesOptions;
import com.github.ka4ok85.wca.response.GetMailingTemplatesResponse;
import com.github.ka4ok85.wca.response.ResponseContainer;
import com.github.ka4ok85.wca.response.containers.MailingTemplate;

@Service
@Scope("prototype")
public class GetMailingTemplatesCommand
		extends AbstractCommand<GetMailingTemplatesResponse, GetMailingTemplatesOptions> {

	private static final String apiMethodName = "GetMailingTemplates";
	private static final Logger log = LoggerFactory.getLogger(GetMailingTemplatesCommand.class);

	@Autowired
	private GetMailingTemplatesResponse getMailingTemplatesResponse;

	@Override
	public ResponseContainer<GetMailingTemplatesResponse> executeCommand(GetMailingTemplatesOptions options)
			throws FailedGetAccessTokenException, FaultApiResultException, BadApiResultException {
		Objects.requireNonNull(options, "GetMailingTemplatesOptions must not be null");

		Element methodElement = doc.createElement(apiMethodName);
		currentNode = addChildNode(methodElement, null);

		Element visibility = doc.createElement("VISIBILITY");
		visibility.setTextContent(options.getVisibility().value().toString());
		addChildNode(visibility, currentNode);

		final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
		if (options.getLastModifiedStartDate() != null && options.getLastModifiedEndDate() != null) {
			if (options.getLastModifiedStartDate().isAfter(options.getLastModifiedEndDate())) {
				throw new RuntimeException("Start Date must be before End Date. Start Date: "
						+ options.getLastModifiedStartDate().format(formatter) + ", End Date: "
						+ options.getLastModifiedEndDate().format(formatter));
			}
		}

		if (options.getLastModifiedStartDate() != null) {
			Element lastModifiedStartDate = doc.createElement("LAST_MODIFIED_START_DATE");
			lastModifiedStartDate.setTextContent(options.getLastModifiedStartDate().format(formatter));
			addChildNode(lastModifiedStartDate, currentNode);
		}

		if (options.getLastModifiedEndDate() != null) {
			Element lastModifiedEndDate = doc.createElement("LAST_MODIFIED_END_DATE");
			lastModifiedEndDate.setTextContent(options.getLastModifiedEndDate().format(formatter));
			addChildNode(lastModifiedEndDate, currentNode);
		}

		if (options.isCrmEnabled()) {
			addBooleanParameter(methodElement, "IS_CRM_ENABLED", options.isCrmEnabled());
		}

		String xml = getXML();
		log.debug("XML Request is {}", xml);
		Node resultNode = runApi(xml);

		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();

		List<MailingTemplate> mailingTempaltes = new ArrayList<MailingTemplate>();

		try {
			DateTimeFormatter formatterUs = DateTimeFormatter.ofPattern("M/d/yy K:mm a");
			NodeList mailingsNode = (NodeList) xpath.evaluate("MAILING_TEMPLATE", resultNode, XPathConstants.NODESET);
			Node mailingNode;
			for (int i = 0; i < mailingsNode.getLength(); i++) {
				MailingTemplate mailingTemplate = new MailingTemplate();
				mailingNode = mailingsNode.item(i);

				Node allowCrmBlockNode = (Node) xpath.evaluate("ALLOW_CRM_BLOCK", mailingNode, XPathConstants.NODE);
				if (allowCrmBlockNode != null) {
					mailingTemplate.setAllowCrmBlock(Boolean.parseBoolean(allowCrmBlockNode.getTextContent()));
				}

				mailingTemplate.setFlaggedForBackup(Boolean
						.parseBoolean(((Node) xpath.evaluate("FLAGGED_FOR_BACKUP", mailingNode, XPathConstants.NODE))
								.getTextContent()));
				mailingTemplate.setLastModified(LocalDateTime.parse(
						((Node) xpath.evaluate("LAST_MODIFIED", mailingNode, XPathConstants.NODE)).getTextContent(),
						formatterUs));
				mailingTemplate.setMailingId(Long.parseLong(
						((Node) xpath.evaluate("MAILING_ID", mailingNode, XPathConstants.NODE)).getTextContent()));
				mailingTemplate.setMailingName(
						((Node) xpath.evaluate("MAILING_NAME", mailingNode, XPathConstants.NODE)).getTextContent());
				mailingTemplate.setSubject(
						((Node) xpath.evaluate("SUBJECT", mailingNode, XPathConstants.NODE)).getTextContent());
				mailingTemplate.setUserId(
						((Node) xpath.evaluate("USER_ID", mailingNode, XPathConstants.NODE)).getTextContent());
				Visibility visibilityValue = Visibility.getVisibilityByAlias(
						((Node) xpath.evaluate("VISIBILITY", mailingNode, XPathConstants.NODE)).getTextContent());
				mailingTemplate.setVisibility(visibilityValue);

				mailingTempaltes.add(mailingTemplate);
			}
		} catch (XPathExpressionException | JobBadStateException e) {
			throw new EngageApiException(e.getMessage());
		}

		getMailingTemplatesResponse.setMailingTempaltes(mailingTempaltes);

		ResponseContainer<GetMailingTemplatesResponse> response = new ResponseContainer<GetMailingTemplatesResponse>(
				getMailingTemplatesResponse);

		return response;
	}
}
