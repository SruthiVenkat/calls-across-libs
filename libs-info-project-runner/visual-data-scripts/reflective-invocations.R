library(sjPlot)
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-invocations.tsv", sep='\t')
filteredDf <- df[df$CalleeVisibility != "public", ]
aggDf <- aggregate(filteredDf$Count, by=list(filteredDf$ActualCalleeLibrary,filteredDf$CalleeVisibility), FUN=length)
tab_df(
  aggDf,
  title = "Reflective Invocations",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)

