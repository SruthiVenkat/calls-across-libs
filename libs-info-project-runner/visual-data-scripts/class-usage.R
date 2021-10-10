library(sjPlot)
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-classUsage.tsv", sep='\t')
filteredDf <- df[df$ClassVisibility != "public", ]
aggDf <- aggregate(filteredDf$Count, by=list(filteredDf$ClassLibrary,filteredDf$ClassVisibility), FUN=length)
tab_df(
  aggDf,
  title = "Class Usage",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)

