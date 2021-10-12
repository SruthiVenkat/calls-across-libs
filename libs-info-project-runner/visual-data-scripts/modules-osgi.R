library(sjPlot)
library(xtable)

# modules

# osgi
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ3-osgiBypasses.tsv", sep='\t')

tab_df(
  df,
  title = "OSGI Bypasses",
  footnote = NULL,
  sort.column = 1,
  col.header = c("Client", "Callee Name", "Callee Library", "Callee Type"),
  CSS = list(css.centeralign = 'text-align: left;')
)

print(xtable(df,
             caption = "OSGI Bypasses", digits = 0, colnames(c("Client", "Callee Name", "Callee Library", "Callee Type"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/rq3-osgi-bypasses.tex",size="small")
