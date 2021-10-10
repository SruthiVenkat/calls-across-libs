library(sjPlot)
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-invocations.tsv", sep='\t')
filteredDf <- df[df$CalleeVisibility != "public", ]
aggDf <- aggregate(filteredDf$Count, by=list(filteredDf$ActualCalleeLibrary,filteredDf$CalleeVisibility), FUN=length)
totalsDf <- aggregate(df$Count, by=list(df$ActualCalleeLibrary), FUN=length)
finalDf <- merge(aggDf, totalsDf, by=1)
finalDf$x.y[duplicated(finalDf$Group.1, finalDf$x.y)] <- ""
tab_df(
  finalDf,
  title = "Reflective Invocations",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count", "Total"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)


