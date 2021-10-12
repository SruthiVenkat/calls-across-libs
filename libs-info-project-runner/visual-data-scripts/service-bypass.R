library(sjPlot)
library(xtable)
library(stringr)

# service bypass methods
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypassCalls.tsv", sep='\t')
aggDf <- aggregate(df$Count, by=list(df$CallerLibrary,df$InterfaceLibrary,df$InterfaceName,df$ImplLibrary,df$ImplName), FUN=length)
colnames(aggDf) <- c("Client", "Interface Library", "Interface", "Implementation Library", "Implementation", "Count")
print(aggDf)

tab_df(
  aggDf,
  title = "No. of methods called beyond published interface",
  footnote = NULL,
  sort.column = 1,
  col.header = c("Client", "Interface Library", "Interface", "Implementation Library", "Implementation", "Count"),
  CSS = list(css.centeralign = 'text-align: left;')
)

print(xtable(aggDf,
             caption = "No. of methods called beyond published interface", digits = 0, colnames(c("Client", "Interface Library", "Interface", "Implementation Library", "Implementation", "Count"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/rq2-service-bypass-methods.tex",size="small")

# service bypass
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypass.tsv", sep='\t')
servicesInfo <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/services-info.tsv", sep='\t')
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-libsInfo.tsv", full.names = TRUE)
finalDf <- data.frame("CallerLibrary"=character(), "CallerMethod"=character(), "DeclaredCalleeMethod"=character(),	
                      "DeclaredCalleeLibrary"=character(), "ActualCalleeMethod"=character(), "ActualCalleeLibrary"=character(),	
                      "CalleeVisibility"==character(),	"ServiceBypass"==character(),	"Counts"=integer(), "Interface"=character(),
                      "Interface Library"=character(), "Implementation"=character())

getLib <- function(cls) {
  for (i in seq_along(file_list)) {
    filename = file_list[[i]]
    libsDf <- read.csv(filename, sep='\t')
    for( j in rownames(libsDf) ) {
      classes = str_split(libsDf[j, "Classes"], ":")
      for (c in classes[[1]]) {
        if (is.na(c[[1]])) break
        if (cls == c[[1]]) {
          return(libsDf[j, "Library.Name"])
        }
      }
    }
  }
  return("unknown")
}
interfaceLibs <- hash()
for( i in rownames(df) ) {
  calledClassName <- str_split(df[i,"ActualCalleeMethod"], "::")[[1]][1]
  for( j in rownames(servicesInfo)) {
    if (str_split(servicesInfo[j, "SPI.Implementations"], ";")[[1]][1] == calledClassName) {
      interface <- servicesInfo[j, "SPI"]
      break
    }
  }
  if (has.key(interface, interfaceLibs)) {
    interfaceLib = interfaceLibs[[interface]]
  } else {
    interfaceLib = getLib(interface)
    interfaceLibs[[interface]] = interfaceLib
  }
  row = data.frame(list(df[i,], "Interface"=interface, "Interface.Library"=interfaceLib, "Implementation"=calledClassName))
  finalDf = rbind(finalDf, row)
}
aggDf <- aggregate(finalDf$Count, by=list(finalDf$CallerLibrary,finalDf$ActualCalleeLibrary,finalDf$Implementation,finalDf$Interface.Library,finalDf$Interface,finalDf$ServiceBypass), FUN=length)

# temporary - till I fix versions - todo
tmpDf = subset(aggDf, Group.3!=Group.5)


tab_df(
  tmpDf,
  title = "Service Bypasses",
  footnote = NULL,
  sort.column = 1,
  col.header = c("Client", "Implementation Library", "Implementation", "Interface Library", "Interface", "Bypass", "Count"),
  CSS = list(css.centeralign = 'text-align: left;')
)

print(xtable(tmpDf,
             caption = "Service Bypasses", digits = 0, colnames(c("Client", "Implementation Library", "Implementation", "Interface Library", "Interface", "Bypass", "Count"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/rq2-service-bypasses.tex",size="small")

