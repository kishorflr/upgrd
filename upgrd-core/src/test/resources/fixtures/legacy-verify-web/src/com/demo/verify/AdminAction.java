package com.demo.verify;

import org.apache.log4j.Logger;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Seasonal admin flow — not present in sample logs (UNOBSERVED). */
public class AdminAction extends Action {
    private static Logger log = Logger.getLogger(AdminAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.info("admin maintenance");
        return mapping.findForward("success");
    }
}
