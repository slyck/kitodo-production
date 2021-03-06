/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.services.data;

import com.sun.research.ws.wadl.HTTPMethods;

import de.sub.goobi.helper.ProjectHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.index.query.QueryBuilder;
import org.goobi.production.flow.statistics.StepInformation;
import org.goobi.webapi.beans.Field;
import org.json.simple.JSONObject;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.helper.enums.IndexAction;
import org.kitodo.data.database.persistence.ProjectDAO;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.elasticsearch.index.Indexer;
import org.kitodo.data.elasticsearch.index.type.ProjectType;
import org.kitodo.data.elasticsearch.search.Searcher;
import org.kitodo.data.elasticsearch.search.enums.SearchCondition;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.dto.ProjectDTO;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.data.base.TitleSearchService;

public class ProjectService extends TitleSearchService<Project, ProjectDTO> {

    private List<StepInformation> commonWorkFlow = null;
    private ProjectDAO projectDAO = new ProjectDAO();
    private ProjectType projectType = new ProjectType();
    private final ServiceManager serviceManager = new ServiceManager();
    private static final Logger logger = LogManager.getLogger(ProjectService.class);

    /**
     * Constructor with Searcher and Indexer assigning.
     */
    public ProjectService() {
        super(new Searcher(Project.class));
        this.indexer = new Indexer<>(Project.class);
    }

    /**
     * Method saves project object to database.
     *
     * @param project
     *            object
     */
    @Override
    public void saveToDatabase(Project project) throws DAOException {
        projectDAO.save(project);
    }

    /**
     * Method saves project document to the index of Elastic Search.
     *
     * @param project
     *            object
     */
    @Override
    @SuppressWarnings("unchecked")
    public void saveToIndex(Project project) throws CustomResponseException, IOException {
        indexer.setMethod(HTTPMethods.PUT);
        if (project != null) {
            indexer.performSingleRequest(project, projectType);
        }
    }

    /**
     * Method saves processes and users related to modified project.
     *
     * @param project
     *            object
     */
    @Override
    protected void manageDependenciesForIndex(Project project)
            throws CustomResponseException, DataException, IOException {
        manageProcessesDependenciesForIndex(project);
        manageUsersDependenciesForIndex(project);
    }

    /**
     * Management od processes for project object.
     * 
     * @param project
     *            object
     */
    private void manageProcessesDependenciesForIndex(Project project) throws CustomResponseException, IOException {
        if (project.getIndexAction() == IndexAction.DELETE) {
            for (Process process : project.getProcesses()) {
                serviceManager.getProcessService().removeFromIndex(process);
            }
        } else {
            for (Process process : project.getProcesses()) {
                serviceManager.getProcessService().saveToIndex(process);
            }
        }
    }

    /**
     * Management od users for project object.
     *
     * @param project
     *            object
     */
    private void manageUsersDependenciesForIndex(Project project) throws CustomResponseException, IOException {
        if (project.getIndexAction() == IndexAction.DELETE) {
            for (User user : project.getUsers()) {
                user.getProjects().remove(project);
                serviceManager.getUserService().saveToIndex(user);
            }
        } else {
            for (User user : project.getUsers()) {
                serviceManager.getUserService().saveToIndex(user);
            }
        }
    }

    @Override
    public List<ProjectDTO> findAll(String sort, Integer offset, Integer size) throws DataException {
        return convertJSONObjectsToDTOs(findAllDocuments(sort, offset, size), false);
    }

    @Override
    public Project getById(Integer id) throws DAOException {
        return projectDAO.find(id);
    }

    @Override
    public List<Project> getAll() {
        return projectDAO.findAll();
    }

    @Override
    public Long countDatabaseRows() throws DAOException {
        return projectDAO.count("FROM Project");
    }

    @Override
    public Long countDatabaseRows(String query) throws DAOException {
        return projectDAO.count(query);
    }

    /**
     * Method removes project object from database.
     *
     * @param project
     *            object
     */
    @Override
    public void removeFromDatabase(Project project) throws DAOException {
        projectDAO.remove(project);
    }

    /**
     * Method removes project object from database.
     *
     * @param id
     *            of project object
     */
    @Override
    public void removeFromDatabase(Integer id) throws DAOException {
        projectDAO.remove(id);
    }

    /**
     * Method removes project object from index of Elastic Search.
     *
     * @param project
     *            object
     */
    @Override
    @SuppressWarnings("unchecked")
    public void removeFromIndex(Project project) throws CustomResponseException, IOException {
        indexer.setMethod(HTTPMethods.DELETE);
        if (project != null) {
            indexer.performSingleRequest(project, projectType);
        }
    }

    @Override
    public List<Project> getByQuery(String query) throws DAOException {
        return projectDAO.search(query);
    }

    /**
     * Find projects for exact start date.
     *
     * @param startDate
     *            of the searched projects as Date
     * @param searchCondition
     *            as SearchCondition - bigger, smaller and so on
     * @return list of JSON objects
     */
    List<JSONObject> findByStartDate(Date startDate, SearchCondition searchCondition) throws DataException {
        QueryBuilder query = createSimpleCompareDateQuery("startDate", startDate, searchCondition);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find projects for exact end date.
     *
     * @param endDate
     *            of the searched projects as Date
     * @param searchCondition
     *            as SearchCondition - bigger, smaller and so on
     * @return list of JSON objects
     */
    List<JSONObject> findByEndDate(Date endDate, SearchCondition searchCondition) throws DataException {
        QueryBuilder query = createSimpleCompareDateQuery("endDate", endDate, searchCondition);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find projects for exact amount of pages.
     * 
     * @param numberOfPages
     *            as Integer
     * @param searchCondition
     *            as SearchCondition - bigger, smaller and so on
     * @return list of JSON objects
     */
    List<JSONObject> findByNumberOfPages(Integer numberOfPages, SearchCondition searchCondition)
            throws DataException {
        QueryBuilder query = createSimpleCompareQuery("numberOfPages", numberOfPages, searchCondition);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find projects for exact amount of volumes.
     *
     * @param numberOfVolumes
     *            as Integer
     * @param searchCondition
     *            as SearchCondition - bigger, smaller and so on
     * @return list of JSON objects
     */
    List<JSONObject> findByNumberOfVolumes(Integer numberOfVolumes, SearchCondition searchCondition)
            throws DataException {
        QueryBuilder query = createSimpleCompareQuery("numberOfVolumes", numberOfVolumes, searchCondition);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find archived or not archived projects.
     * 
     * @param archived
     *            if true - find archived projects, if false - find not archived
     *            projects
     * @return list of JSON objects
     */
    List<JSONObject> findByArchived(Boolean archived) throws DataException {
        QueryBuilder query = createSimpleQuery("archived", archived.toString(), true);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find project by id of process.
     *
     * @param id
     *            of process
     * @return search result
     */
    public JSONObject findByProcessId(Integer id) throws DataException {
        QueryBuilder query = createSimpleQuery("processes.id", id, true);
        return searcher.findDocument(query.toString());
    }

    /**
     * Find projects by title of process.
     *
     * @param title
     *            of process
     * @return list of JSON objects with projects for specific process title
     */
    public List<JSONObject> findByProcessTitle(String title) throws DataException {
        Set<Integer> processIds = new HashSet<>();

        List<JSONObject> processes = serviceManager.getProcessService().findByTitle(title, true);
        for (JSONObject process : processes) {
            processIds.add(getIdFromJSONObject(process));
        }
        return searcher.findDocuments(createSetQuery("processes.id", processIds, true).toString());
    }

    /**
     * Find project by id of user.
     *
     * @param id
     *            of user
     * @return list of JSON objects
     */
    List<JSONObject> findByUserId(Integer id) throws DataException {
        QueryBuilder query = createSimpleQuery("users.id", id, true);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find projects by login of user.
     *
     * @param login
     *            of user
     * @return list of search result with projects for specific user login
     */
    List<JSONObject> findByUserLogin(String login) throws DataException {
        JSONObject user = serviceManager.getUserService().findByLogin(login);
        return findByUserId(getIdFromJSONObject(user));
    }

    /**
     * Get all projects from index an convert them for frontend.
     *
     * @return list of ProjectDTO objects
     */
    public List<ProjectDTO> findAll() throws DataException {
        return convertJSONObjectsToDTOs(findAllDocuments(), false);
    }

    /**
     * Get all projects sorted by title.
     *
     * @return all projects sorted by title as Project objects
     */
    public List<Project> getAllProjectsSortedByTitle() throws DAOException {
        return projectDAO.getAllProjectsSortedByTitle();
    }

    /**
     * Get all not archived projects sorted by title.
     *
     * @return all not archived projects sorted by title as Project objects
     */
    public List<Project> getAllNotArchivedProjectsSortedByTitle() throws DAOException {
        return projectDAO.getAllNotArchivedProjectsSortedByTitle();
    }

    /**
     * Method adds all object found in database to Elastic Search index.
     */
    @SuppressWarnings("unchecked")
    public void addAllObjectsToIndex() throws CustomResponseException, InterruptedException, IOException {
        indexer.setMethod(HTTPMethods.PUT);
        indexer.performMultipleRequests(getAll(), projectType);
    }

    @Override
    public ProjectDTO convertJSONObjectToDTO(JSONObject jsonObject, boolean related) throws DataException {
        ProjectDTO projectDTO = new ProjectDTO();
        projectDTO.setId(getIdFromJSONObject(jsonObject));
        projectDTO.setTitle(getStringPropertyForDTO(jsonObject, "title"));
        projectDTO.setStartDate(getStringPropertyForDTO(jsonObject, "startDate"));
        projectDTO.setEndDate(getStringPropertyForDTO(jsonObject, "endDate"));
        projectDTO.setFileFormatDmsExport(getStringPropertyForDTO(jsonObject, "fileFormatDmsExport"));
        projectDTO.setFileFormatInternal(getStringPropertyForDTO(jsonObject, "fileFormatInternal"));
        projectDTO.setNumberOfPages(getIntegerPropertyForDTO(jsonObject, "numberOfPages"));
        projectDTO.setNumberOfVolumes(getIntegerPropertyForDTO(jsonObject, "numberOfVolumes"));
        //projectDTO.setProjectIsArchived(getStringPropertyForDTO(jsonObject, "archived"));
        if (!related) {
            projectDTO = convertRelatedJSONObjects(jsonObject, projectDTO);
        }
        return projectDTO;
    }

    private ProjectDTO convertRelatedJSONObjects(JSONObject jsonObject, ProjectDTO projectDTO) throws DataException {
        projectDTO.setProcesses(convertRelatedJSONObjectToDTO(jsonObject, "processes", serviceManager.getProcessService()));
        projectDTO.setUsers(convertRelatedJSONObjectToDTO(jsonObject, "users", serviceManager.getUserService()));
        return projectDTO;
    }

    /**
     * Get workflow.
     *
     * @return a list with information for each step on workflow
     */

    public List<StepInformation> getWorkFlow(Project project) {
        if (this.commonWorkFlow == null) {
            if (project.getId() != null) {
                this.commonWorkFlow = ProjectHelper.getProjectWorkFlowOverview(project);
            } else {
                this.commonWorkFlow = new ArrayList<>();
            }
        }
        return this.commonWorkFlow;
    }

    @XmlElement(name = "field")
    public List<Field> getFieldConfig(Project project) throws IOException {
        return Field.getFieldConfigForProject(project);
    }
}
