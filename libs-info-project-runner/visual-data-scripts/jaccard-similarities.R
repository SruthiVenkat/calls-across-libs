library(sjPlot)
library(xtable)
library(stringr)

file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="RQ4-jaccard-*", full.names = TRUE)
#for (i in seq_along(file_list)) {
  filename = file_list[[10]]
  df <- read.csv(filename, sep='\t')
  lib <- str_split(str_split(filename, "jaccard-")[[1]][2], ".tsv")[[1]][1]
  
  tab_df(
    df,
    title = paste("Jaccard Similarity - ",lib, sep = ""),
    footnote = NULL,
    sort.column = 1,
    digits = 5,
    CSS = list(css.centeralign = 'text-align: left;')
  )
  
  #print(xtable(df,
   #            caption = paste("Jaccard Similarity - ",lib, sep = ""), digits = 5), 
    #    file = paste("Documents/Waterloo/PL/21.icse.library-usage/rq4-jaccard-", lib, ".tex", sep = ""),size="small")
  
#}

