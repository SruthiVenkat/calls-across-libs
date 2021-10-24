# RQ3 - crossing Java9-module-info and OSGI boundaries
library(hash)
library(dplyr)
library(tidyr)

java9modulesDataFrame = read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/java9modulesInfo.tsv", sep='\t')
java9modulesData <- hash()
for(i in 1:nrow(java9modulesDataFrame)) 
{
  if (java9modulesDataFrame[i,1]!="")
    java9modulesData[[paste(strsplit(java9modulesDataFrame[i,1], ":")[[1]][[1]], strsplit(java9modulesDataFrame[i,1], ":")[[1]][[2]], sep=":")]] <- java9modulesDataFrame[i,6][[1]]
}

osgiDataFrame = read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/osgiInfo.tsv", sep='\t')
osgiData <- hash()
for(i in 1:nrow(osgiDataFrame)) 
{
  libKey <- paste(strsplit(osgiDataFrame[i,1], ":")[[1]][[1]], strsplit(osgiDataFrame[i,1], ":")[[1]][[2]], sep=":")
  if(is.null(osgiData[[libKey]]))
    osgiData[[libKey]] <- osgiDataFrame[i,2][[1]]
  else
    osgiData[[libKey]] <- paste(osgiData[[libKey]], osgiDataFrame[i,2][[1]], sep=",")
}

writeLines("CallerLibrary\tCalleeName\tCalleeLibrary\tCalleeType","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ3-osgiBypasses.tsv")
writeLines("CallerLibrary\tCalleeName\tCalleeLibrary\tCalleeType","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ3-moduleBypasses.tsv")

returnInvocationsDataRow <- function(df, j) {
  dataRow = data.frame(df[j,2], df[j,6],df[j,7], "method")
  return(dataRow)
}

returnFieldsDataRow <- function(df, j) {
  dataRow = data.frame(df[j,1], paste(df[j,2],df[j,5],";"),df[j,8], "field")
  return(dataRow)
}

returnClassUsageDataRow <- function(df, j) {
  dataRow = data.frame(df[j,5], df[j,1],df[j,3], df[j,4])
  return(dataRow)
}

returnAnnotationsDataRow <- function(df, j) {
  dataRow = data.frame(df[j,4], paste(df[j,5],df[j,6],";"),df[j,7], "annotations")
  return(dataRow)
}

returnSubtypingDataRow <- function(df, j) {
  dataRow = data.frame(df[j,2], paste(df[j,3],df[j,4],";"),df[j,5], "subtyping")
  return(dataRow)
}

returnSetAccessibleDataRow <- function(df, j) {
  dataRow = data.frame(df[j,2], df[j,5],df[j,7], "setAccessible")
  return(dataRow)
}

detectOSGIModuleBypasses <- function(file_list, calleeIndex, libIndex, fn) {
  for (i in seq_along(file_list)) {
    filename = file_list[[i]]
    df <- read.csv(filename, sep='\t')
    for(j in 1:nrow(df)) 
    {
      if (is.character(df[j,libIndex])) {
        libGAV = strsplit(df[j,libIndex], ":")
        
        if (identical(libGAV[[1]], character(0)))
          next
        
        if(length(libGAV[[1]])>=3) {
          lib <- paste(libGAV[[1]][[1]], libGAV[[1]][[2]], sep=":")
        } else {
          lib <- libGAV[[1]][[1]]
        }
        actualCalleeClassName = strsplit(df[j,calleeIndex], "::")[[1]][[1]]
        pkg = substr(actualCalleeClassName,1,stringi::stri_locate_last_fixed(actualCalleeClassName, ".")[,1]-1)
        if (is.character(osgiData[[lib]])) { # checks if lib exists in osgiData
          osgiExports = strsplit(osgiData[[lib]], ",")
          if (!(pkg %in% osgiExports[[1]])) {
            dataRow = fn(df, j)  %>% drop_na()
            if(dataRow[1]!=dataRow[3])
              write.table(dataRow,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ3-osgiBypasses.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
            
          }
        }
        if (is.character(java9modulesData[[lib]])) { # checks if lib exists in osgiData
          moduleExports = strsplit(java9modulesData[[lib]], ";")
          if (!(pkg %in% moduleExports[[1]])) {
            dataRow = fn(df, j)  %>% drop_na()
            if(dataRow[1]!=dataRow[3])
              write.table(dataRow,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ3-moduleBypasses.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
          }
        }
      }
    }
  }
}

# invocations 
detectOSGIModuleBypasses(list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)
                         ,6, 7, returnInvocationsDataRow)
# fields 
detectOSGIModuleBypasses(list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-fields.tsv", full.names = TRUE)
                         ,2, 8, returnFieldsDataRow)
# class usage
detectOSGIModuleBypasses(list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-classesUsageInfo.tsv", full.names = TRUE)
                         ,1, 3, returnClassUsageDataRow)

# annotations
detectOSGIModuleBypasses(list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-annotations.tsv", full.names = TRUE)
                         ,5, 7, returnAnnotationsDataRow)
# subtyping
detectOSGIModuleBypasses(list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-subtyping.tsv", full.names = TRUE)
                         ,3, 5, returnSubtypingDataRow)
# setAccessible
detectOSGIModuleBypasses(list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-setAccessibleCalls.tsv", full.names = TRUE)
                         ,5, 7, returnSetAccessibleDataRow)

