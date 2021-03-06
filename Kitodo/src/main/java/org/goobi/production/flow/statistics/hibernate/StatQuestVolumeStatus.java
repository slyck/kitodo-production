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

package org.goobi.production.flow.statistics.hibernate;

import de.intranda.commons.chart.renderer.HtmlTableRenderer;
import de.intranda.commons.chart.renderer.IRenderer;
import de.intranda.commons.chart.results.DataRow;
import de.intranda.commons.chart.results.DataTable;
import de.sub.goobi.helper.Helper;

import java.util.ArrayList;
import java.util.List;

import org.goobi.production.flow.statistics.IDataSource;
import org.goobi.production.flow.statistics.IStatisticalQuestion;
import org.goobi.production.flow.statistics.enums.CalculationUnit;
import org.goobi.production.flow.statistics.enums.StatisticsMode;
import org.goobi.production.flow.statistics.enums.TimeUnit;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.kitodo.data.database.beans.Task;

/**
 * Implementation of {@link IStatisticalQuestion}. Statistical Request with
 * predefined Values in data Table
 * 
 * @author Steffen Hankiewicz
 */
public class StatQuestVolumeStatus implements IStatisticalQuestion {

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.goobi.production.flow.statistics.IStatisticalQuestion#getDataTables(
     * org.goobi.production.flow.statistics.IDataSource)
     */
    @Override
    public List<DataTable> getDataTables(List dataSource) {

        IEvaluableFilter originalFilter;

        if (dataSource instanceof IEvaluableFilter) {
            originalFilter = (IEvaluableFilter) dataSource;
        } else {
            throw new UnsupportedOperationException(
                    "This implementation of IStatisticalQuestion needs an IDataSource for method getDataSets()");
        }

        Criteria crit = Helper.getHibernateSession().createCriteria(Task.class);
        crit.add(Restrictions.or(Restrictions.eq("processingStatus", 1), Restrictions.like("processingStatus", 2)));

        if (originalFilter instanceof UserDefinedFilter) {
            crit.createCriteria("process", "proz");
            crit.add(Restrictions.in("proz.id", originalFilter.getIDList()));
        }
        String title = StatisticsMode.getByClassName(this.getClass()).getTitle();

        DataTable dtbl = new DataTable(title);
        dtbl.setShowableInPieChart(true);
        DataRow dRow = new DataRow(Helper.getTranslation("count"));

        for (Object obj : crit.list()) {
            Task step = (Task) obj;
            String kurztitel = (step.getTitle().length() > 60 ? step.getTitle().substring(0, 60) + "..."
                    : step.getTitle());
            dRow.addValue(kurztitel, dRow.getValue(kurztitel) + 1);
        }

        dtbl.addDataRow(dRow);
        List<DataTable> allTables = new ArrayList<>();

        dtbl.setUnitLabel(Helper.getTranslation("arbeitsschritt"));
        allTables.add(dtbl);
        return allTables;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.goobi.production.flow.statistics.IStatisticalQuestion#
     * isRendererInverted( de.intranda.commons.chart.renderer.IRenderer)
     */
    @Override
    public Boolean isRendererInverted(IRenderer inRenderer) {
        return inRenderer instanceof HtmlTableRenderer;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.goobi.production.flow.statistics.IStatisticalQuestion#
     * setCalculationUnit(
     * org.goobi.production.flow.statistics.enums.CalculationUnit)
     */
    @Override
    public void setCalculationUnit(CalculationUnit cu) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.goobi.production.flow.statistics.IStatisticalQuestion#setTimeUnit(
     * org.goobi.production.flow.statistics.enums.TimeUnit)
     */
    @Override
    public void setTimeUnit(TimeUnit timeUnit) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.goobi.production.flow.statistics.IStatisticalQuestion#
     * getNumberFormatPattern()
     */
    @Override
    public String getNumberFormatPattern() {
        return "#";
    }

}
