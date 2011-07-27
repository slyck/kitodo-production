package org.goobi.production.flow.statistics.hibernate;

/**
 * This file is part of the Goobi Application - a Workflow tool for the support of 
 * mass digitization.
 * 
 * Visit the websites for more information. 
 *   - http://gdz.sub.uni-goettingen.de 
 *   - http://www.intranda.com 
 * 
 * Copyright 2009, Center for Retrospective Digitization, Göttingen (GDZ),
 * 
 * This program is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License as published by the Free Software Foundation; 
 * either version 2 of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; 
 * if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, 
 * Boston, MA 02111-1307 USA
 * 
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.text.StrTokenizer;
import org.apache.log4j.Logger;
import org.goobi.production.flow.IlikeExpression;
import org.goobi.production.flow.statistics.hibernate.UserDefinedFilter.Parameters;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Restrictions;

import de.sub.goobi.Beans.Benutzer;
import de.sub.goobi.Beans.Projekt;
import de.sub.goobi.Beans.Prozess;
import de.sub.goobi.Forms.LoginForm;
import de.sub.goobi.Persistence.BenutzerDAO;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.PaginatingCriteria;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;

/**
 * class provides methods used by implementations of IEvaluableFilter
 * 
 * @author Wulf Riebensahm
 * 
 */
class BatchHelper {

	private static final Logger logger = Logger.getLogger(BatchHelper.class);

	/**
	 * limit query to project (formerly part of ProzessverwaltungForm)
	 * 
	 * @param crit
	 */
	protected static void limitToUserAccessRights(Conjunction con) {
		/* restriction to specific projects if not with admin rights */
		LoginForm loginForm = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
		Benutzer aktuellerNutzer = null;
		try {
			aktuellerNutzer = new BenutzerDAO().get(loginForm.getMyBenutzer().getId());
		} catch (DAOException e) {
			logger.warn("DAOException", e);
		}
		if (aktuellerNutzer != null) {
			if (loginForm.getMaximaleBerechtigung() > 1) {
				Disjunction dis = Restrictions.disjunction();
				for (Projekt proj : aktuellerNutzer.getProjekteList()) {
					dis.add(Restrictions.eq("projekt", proj));
				}
				con.add(dis);
			}
		}
	}

	/**
	 * This functions extracts the Integer from the parameters passed with the
	 * step filter in first positon.
	 * 
	 * @param String
	 *            parameter
	 * @author Wulf Riebensahm
	 * @return Integer
	 ****************************************************************************/
	protected static Integer getStepStart(String parameter) {
		String[] strArray = parameter.split("-");
		return Integer.parseInt(strArray[0]);
	}

	/**
	 * This functions extracts the Integer from the parameters passed with the
	 * step filter in last positon.
	 * 
	 * @param String
	 *            parameter
	 * @author Wulf Riebensahm
	 * @return Integer
	 ****************************************************************************/
	protected static Integer getStepEnd(String parameter) {
		String[] strArray = parameter.split("-");
		return Integer.parseInt(strArray[1]);
	}

	/**
	 * This function analyzes the parameters on a step filter and returns a
	 * StepFilter enum to direct further processing it reduces the necessity to
	 * apply some filter keywords
	 * 
	 * @param String
	 *            parameters
	 * @author Wulf Riebensahm
	 * @return StepFilter
	 ****************************************************************************/
	protected static StepFilter getStepFilter(String parameters) {

		if (parameters.contains("-")) {
			String[] strArray = parameters.split("-");
			if (!(strArray.length < 2)) {
				if (strArray[0].length() == 0) {
					return StepFilter.max;
				} else {
					return StepFilter.range;
				}
			} else {
				return StepFilter.min;
			}
		} else if (!parameters.contains("-")) {
			try {
				// check if parseInt throws an exception
				Integer.parseInt(parameters);
				return StepFilter.exact;
			} catch (NumberFormatException e) {
				return StepFilter.name;
			}
		}
		return StepFilter.unknown;
	}

	/**
	 * This enum represents the result of parsing the step<modifier>: filter
	 * Restrictions
	 ****************************************************************************/
	protected static enum StepFilter {
		exact, range, min, max, name, unknown
	}

	/**
	 * Filter processes for done steps range
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepRange(Conjunction con, String parameters, StepStatus inStatus) {
		con.add(Restrictions.and(
				Restrictions.and(Restrictions.ge("steps.reihenfolge", FilterHelper.getStepStart(parameters)),
						Restrictions.le("steps.reihenfolge", FilterHelper.getStepEnd(parameters))),
				Restrictions.eq("steps.bearbeitungsstatus", inStatus.getValue().intValue())));
	}

	/**
	 * Filter processes for steps name with given status
	 * 
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterStepName(Conjunction con, String parameters, StepStatus inStatus) {
		if (con == null) {
			con = Restrictions.conjunction();
		}
		con.add(Restrictions.and(Restrictions.like("steps.titel", "%" + parameters + "%"),
				Restrictions.eq("steps.bearbeitungsstatus", inStatus.getValue().intValue())));
	}

	/**
	 * Filter processes for done steps min
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepMin(Conjunction con, String parameters, StepStatus inStatus) {
		if (con == null) {
			con = Restrictions.conjunction();
		}
		con.add(Restrictions.and(Restrictions.ge("steps.reihenfolge", FilterHelper.getStepStart(parameters)),
				Restrictions.eq("steps.bearbeitungsstatus", inStatus.getValue().intValue())));
	}

	/**
	 * Filter processes for done steps max
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepMax(Conjunction con, String parameters, StepStatus inStatus) {
		if (con == null) {
			con = Restrictions.conjunction();
		}
		con.add(Restrictions.and(Restrictions.le("steps.reihenfolge", FilterHelper.getStepEnd(parameters)),
				Restrictions.eq("steps.bearbeitungsstatus", inStatus.getValue().intValue())));
	}

	/**
	 * Filter processes for done steps exact
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepExact(Conjunction con, String parameters, StepStatus inStatus) {
		con.add(Restrictions.and(Restrictions.eq("steps.reihenfolge", FilterHelper.getStepStart(parameters)),
				Restrictions.eq("steps.bearbeitungsstatus", inStatus.getValue().intValue())));
	}

	/**
	 * Filter processes for done steps by user
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterStepDoneUser(Conjunction con, String tok) {
		/*
		 * filtering by a certain done step, which the current user finished
		 */
		String login = tok.substring("stepDoneUser:".length());
		con.add(Restrictions.eq("user.login", login));
	}

	/**
	 * Filter processes by project
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterProject(Conjunction con, String tok) {
		/* filter according to linked project */
		con.add(Restrictions.like("proj.titel", "%" + tok.substring(5) + "%"));
	}

	/**
	 * Filter processes by scan template
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterScanTemplate(Conjunction con, String tok) {
		/* Filtering by signature */
		// crit.add(Restrictions.like("vorleig.titel", "%Signatur%"));
		con.add(Restrictions.like("vorleig.wert", "%" + tok.substring(5) + "%"));
	}

	protected static void filterStepProperty(Conjunction con, String tok) {
		/* Filtering by signature */
		String[] ts = tok.substring(8).split(":");
		// crit.add(Restrictions.like("vorleig.titel", "%Signatur%"));

		if (ts.length > 1) {
			con.add(Restrictions.and(Restrictions.like("schritteig.wert", "%" + ts[1] + "%"),
					Restrictions.like("schritteig.titel", "%" + ts[0] + "%")));
		} else {
			con.add(Restrictions.like("schritteig.wert", "%" + tok.substring(8) + "%"));
		}
	}

	protected static void filterProcessProperty(Conjunction con, String tok) {
		/* Filtering by signature */
		// crit.add(Restrictions.like("vorleig.titel", "%Signatur%"));
		/* Filtering by signature */
		String[] ts = tok.substring(tok.indexOf(":") + 1).split(":");
		// crit.add(Restrictions.like("vorleig.titel", "%Signatur%"));

		if (ts.length > 1) {
			con.add(Restrictions.and(Restrictions.like("prozesseig.wert", "%" + ts[1] + "%"),
					Restrictions.like("prozesseig.titel", "%" + ts[0] + "%")));
		} else {
			con.add(Restrictions.like("prozesseig.wert", "%" + ts[0] + "%"));
		}
	}

	/**
	 * Filter processes by Ids
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterIds(Conjunction con, String tok) {
		/* filtering by ids */
		// Disjunction dis = Restrictions.disjunction();
		List<Integer> listIds = new ArrayList<Integer>();
		if (tok.substring(5).length() > 0) {
			// tok.substring(5).split(" ")
			String[] tempids = tok.substring(5).split(" ");
			for (int i = 0; i < tempids.length; i++) {
				int tempid = Integer.parseInt(tempids[i]);
				listIds.add(tempid);
			}
		}
		con.add(Restrictions.in("id", listIds));
	}

	/**
	 * Filter processes by workpiece
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterWorkpiece(Conjunction con, String tok) {
		/* filter according signature */

		con.add(Restrictions.like("werkeig.wert", "%" + tok.substring(5) + "%"));
	}

	/**
	 * This method builds a criteria depending on a filter string and some other
	 * parameters passed on along the initial criteria. The filter is parsed and
	 * depending on which data structures are used for applying filtering
	 * restrictions conjunctions are formed and collect the restrictions and
	 * then will be applied on the corresponding criteria. A criteria is only
	 * added if needed for the presence of filters applying to it.
	 * 
	 * 
	 * @param inFilter
	 * @param crit
	 * @param isTemplate
	 * @param returnParameters
	 *            Object containing values which need to be set and returned to
	 *            UserDefinedFilter
	 * @param userAssignedStepsOnly
	 * @param stepOpenOnly
	 * @return String used to pass on error messages about errors in the filter
	 *         expression
	 */
	protected static String criteriaBuilder(Session session, String inFilter, PaginatingCriteria crit) {
		boolean flagSetCritProjects = false;
	
		// keeping a reference to the passed criteria
		Criteria inCrit = crit;
		@SuppressWarnings("unused")
		Criteria critProject = null;
		Criteria critProcess = null;

		// to collect and return feedback about erroneous use of filter
		// expressions
		String message = new String("");

		StrTokenizer tokenizer = new StrTokenizer(inFilter, ' ', '\"');

		// conjunctions collecting conditions
		Conjunction conjWorkPiece = null;
		Conjunction conjProjects = null;
		Conjunction conjSteps = null;
		Conjunction conjProcesses = null;
		Conjunction conjTemplates = null;
		Conjunction conjUsers = null;
		Conjunction conjStepProperties = null;
		Conjunction conjProcessProperties = null;

	
			conjProjects = Restrictions.conjunction();
			limitToUserAccessRights(conjProjects);
			// in case nothing is set here it needs to be removed again
			// happens if user has admin rights
			if (conjProjects.toString().equals("()")) {
				conjProjects = null;
				flagSetCritProjects = true;
			}
		

		

		
		List<String> aliases = new ArrayList<String>();
		// this is needed for evaluating a filter string
		while (tokenizer.hasNext()) {
			String tok = tokenizer.nextToken().trim();

			if (tok.startsWith("processeig:")) {
				if (conjProcessProperties == null) {
					conjProcessProperties = Restrictions.conjunction();
				}
				FilterHelper.filterProcessProperty(conjProcessProperties, tok);
			} else if (tok.startsWith("stepeig:")) {
				if (conjStepProperties == null) {
					conjStepProperties = Restrictions.conjunction();
				}
				FilterHelper.filterStepProperty(conjStepProperties, tok);
			}

			// search over steps
			// original filter, is left here for compatibility reason
			// doesn't fit into new keyword scheme
			else if (tok.startsWith("step:")) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + createHistoricFilter(conjSteps, tok, false);

			} else if (tok.toLowerCase().startsWith("stepinwork:")) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(null, conjSteps, tok, 11, StepStatus.INWORK));

				// new keyword stepLocked implemented
			} else if (tok.toLowerCase().startsWith("steplocked:")) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(null, conjSteps, tok, 11, StepStatus.LOCKED));

				// new keyword stepOpen implemented
			} else if (tok.toLowerCase().startsWith("stepopen:")) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(null, conjSteps, tok, 9, StepStatus.OPEN));

				// new keyword stepDone implemented
			} else if (tok.toLowerCase().startsWith("stepdone:")) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(null, conjSteps, tok, 9, StepStatus.DONE));

				// new keyword stepDoneTitle implemented, replacing so far
				// undocumented
			} else if (tok.toLowerCase().startsWith("stepdonetitle:")) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				String stepTitel = tok.substring("stepDoneTitle:".length());
				FilterHelper.filterStepName(conjSteps, stepTitel, StepStatus.DONE);

			} else if (tok.toLowerCase().startsWith("stepdoneuser:")) {
				if (conjUsers == null) {
					conjUsers = Restrictions.conjunction();
				}
				FilterHelper.filterStepDoneUser(conjUsers, tok);

			} else if (tok.startsWith("proj:")) {
				if (conjProjects == null) {
					conjProjects = Restrictions.conjunction();
				}
				FilterHelper.filterProject(conjProjects, tok);

			} else if (tok.startsWith("vorl:")) {
				if (conjTemplates == null) {
					conjTemplates = Restrictions.conjunction();
				}
				FilterHelper.filterScanTemplate(conjTemplates, tok);

			} else if (tok.startsWith("idin:")) {
				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}
				FilterHelper.filterIds(conjProcesses, tok);

			} else if (tok.startsWith("proc:")) {
				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}
				conjProcesses.add(Restrictions.like("titel", "%" + tok + "%"));

			} else if (tok.startsWith("werk:")) {
				if (conjWorkPiece == null) {
					conjWorkPiece = Restrictions.conjunction();
				}
				FilterHelper.filterWorkpiece(conjWorkPiece, tok);

			} else if (tok.startsWith("-")) {

				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}

				conjProcesses.add(Restrictions.not(Restrictions.like("titel", "%" + tok.substring(1) + "%")));

			} else {

				/* standard-search parameter */
				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}

				conjProcesses.add(IlikeExpression.ilike("titel", "*" + tok + "*", '!'));
			}
		}

		if (conjProcesses != null) {

					critProcess = crit.createCriteria("prozess", "proc");
					// crit.createAlias("proc.ProjekteID", "projID");
				
				if (conjProcesses != null) {
					// inCrit.add(conjProcesses);
					critProcess.add(conjProcesses);
				}
		
		}

	
		if (conjSteps != null) {
			
				crit.createCriteria("schritte", "steps");
				crit.add(conjSteps);
			
		}

		if (conjTemplates != null) {
			
				crit.createCriteria("vorlagen", "vorl");
				crit.createAlias("vorl.eigenschaften", "vorleig");
				inCrit.add(conjTemplates);
			
		}

		if (conjProcessProperties != null) {
			

				inCrit.createAlias("eigenschaften", "prozesseig");
				inCrit.add(conjProcessProperties);
			
		}

		if (conjStepProperties != null) {
		
				Criteria stepCrit =  session.createCriteria(Prozess.class);
				stepCrit.createCriteria("schritte", "steps");
				stepCrit.createAlias("steps.eigenschaften", "schritteig");
				stepCrit.add(conjStepProperties);
				stepCrit.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
				List<Integer> myIds = new ArrayList<Integer>();
			
		   	    for (Iterator<Prozess> it = stepCrit.setFirstResult(0).setMaxResults(
						    Integer.MAX_VALUE).list().iterator(); it.hasNext();) {
						   Prozess p =  it.next();
						   myIds.add(p.getId());
		   	    }
		   	    crit.add(Restrictions.in("id", myIds));
			
		}

		if (conjWorkPiece != null) {
				critProcess.createCriteria("werkstuecke", "werk");
				critProcess.createAlias("werk.eigenschaften", "werkeig");
				critProcess.add(conjWorkPiece);
		
		}
		if (conjUsers != null) {
			
				critProcess.createCriteria("bearbeitungsbenutzer", "user");
				critProcess.add(conjUsers);
	
		}
		return message;
	}

	/**
	 * 
	 * @param conjSteps
	 * @param filterPart
	 * @return
	 */
	private static String createHistoricFilter(Conjunction conjSteps, String filterPart, Boolean stepCriteria) {
		/* filtering by a certain minimal status */
		Integer stepReihenfolge;
		String stepTitle = filterPart.substring(5);
		// if the criteria is build on steps the table need not be identified
		String tableIdentifier;
		if (stepCriteria) {
			tableIdentifier = "";
		} else {
			tableIdentifier = "steps.";
		}
		try {
			stepReihenfolge = Integer.parseInt(stepTitle);
		} catch (NumberFormatException e) {
			stepTitle = filterPart.substring(5);
			if (stepTitle.startsWith("-")) {
				stepTitle = stepTitle.substring(1);
				conjSteps.add(Restrictions.and(Restrictions.not(Restrictions.like(tableIdentifier + "titel", "%" + stepTitle + "%")),
						Restrictions.ge(tableIdentifier + "bearbeitungsstatus", StepStatus.OPEN.getValue())));
				return "";
			} else {
				conjSteps.add(Restrictions.and(Restrictions.like(tableIdentifier + "titel", "%" + stepTitle + "%"),
						Restrictions.ge(tableIdentifier + "bearbeitungsstatus", StepStatus.OPEN.getValue())));
				return "";
			}
		}
		conjSteps.add(Restrictions.and(Restrictions.eq(tableIdentifier + "reihenfolge", stepReihenfolge),
				Restrictions.ge(tableIdentifier + "bearbeitungsstatus", StepStatus.OPEN.getValue())));
		return "";
	}

	/************************************************************************************
	 * @param flagCriticalQuery
	 * @param crit
	 * @param parameters
	 * @return
	 ************************************************************************************/
	private static String createStepFilters(Parameters returnParameters, Conjunction con, String filterPart, int filterPartTitleLength,
			StepStatus inStatus) {
		// extracting the substring into parameter (filter parameters e.g. 5,
		// -5,
		// 5-10, 5- or "Qualitätssicherung")
		String parameters = filterPart.substring(filterPartTitleLength);
		String message = "";
		/*
		 * -------------------------------- Analyzing the parameters and what
		 * user intended (5->exact, -5 ->max, 5-10 ->range, 5- ->min.,
		 * Qualitätssicherung ->name) handling the filter according to the
		 * parameters --------------------------------
		 */

		switch (FilterHelper.getStepFilter(parameters)) {

		case exact:
			try {
				FilterHelper.filterStepExact(con, parameters, inStatus);
				returnParameters.setStepDone(FilterHelper.getStepStart(parameters));
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				logger.error(e);
				message = "filterpart '" + filterPart.substring(filterPartTitleLength) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case max:
			try {
				FilterHelper.filterStepMax(con, parameters, inStatus);
				returnParameters.setCriticalQuery();
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPartTitleLength) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case min:
			try {
				FilterHelper.filterStepMin(con, parameters, inStatus);
				returnParameters.setCriticalQuery();
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPartTitleLength) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case name:
			/* filter for a specific done step by it's name (Titel) */
			// myObservable.setMessage("Filter 'stepDone:" + parameters
			// + "' is not yet implemented and will be ignored!");
			try {
				FilterHelper.filterStepName(con, parameters, inStatus);
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPartTitleLength) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case range:
			try {
				FilterHelper.filterStepRange(con, parameters, inStatus);
				returnParameters.setCriticalQuery();
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPartTitleLength) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case unknown:
			message = message + ("Filter '" + filterPart + "' is not known!\n");
		}
		return message;
	}

}