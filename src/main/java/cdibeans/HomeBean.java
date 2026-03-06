package cdibeans;

import dyntabs.BaseDyntabCdiBean;
import dyntabs.annotation.DynTab;
import dyntabs.scope.TabScoped;
import jakarta.inject.Named;

@Named
@TabScoped
@DynTab(name = "HomeDynTab", uniqueIdentifier = "Home", title = "Welcome", includePage = "/WEB-INF/include/home/home.xhtml", closeable = false)

public class HomeBean extends BaseDyntabCdiBean {

}
