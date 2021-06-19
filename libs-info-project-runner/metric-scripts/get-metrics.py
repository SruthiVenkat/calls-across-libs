import pandas as pd 
import os
import numpy

def apiProportion(client, library, pathToCsvs):
	#get the number of calls from client to library
	df = pd.read_csv(os.path.join(pathToCsvs,client+'.tsv'),sep='\t')
	callerCallee = df.loc[(df['Callee Library'].str.contains(library))& (df['Caller Library'] == client)]
	uniqueCalleeCount = len(callerCallee["Callee Method"].unique())
	
	#get the number of public methods in the library
	libsInfoDf = pd.read_csv(os.path.join(pathToCsvs,'libs-info.tsv'),sep='\t')
	libraryInfoCount = libsInfoDf.loc[(libsInfoDf['Library Name'].str.contains(library))]['No. of Public/Protected Methods'].values[0]
	
	return uniqueCalleeCount/libraryInfoCount
	
def jaccardSimilarity(clientA, clientB, library, pathToCsvs):
    dfA = pd.read_csv(os.path.join(pathToCsvs,clientA+'.tsv'),sep='\t')
    clientAmethods = dfA.loc[(dfA['Callee Library'].str.contains(library))& (dfA['Caller Library'] == clientA)]
    uniqueLibAMethods = clientAmethods["Callee Method"].unique()

    dfB = pd.read_csv(os.path.join(pathToCsvs,clientB+'.tsv'),sep='\t')
    clientBmethods = dfB.loc[(dfB['Callee Library'].str.contains(library))& (dfB['Caller Library'] == clientB)]
    uniqueLibBMethods = clientBmethods["Callee Method"].unique()

    intersectMethods = numpy.intersect1d(uniqueLibAMethods,uniqueLibBMethods)
    unionMethods = numpy.union1d(uniqueLibAMethods, uniqueLibBMethods)
    return len(intersectMethods)/len(unionMethods)
    
	
def Q1(library, pathToCsvs):
	libsInfoDf = pd.read_csv(os.path.join(pathToCsvs,'libs-info.tsv'),sep='\t')
	noMethodsCalledByTests = libsInfoDf.loc[(libsInfoDf['Library Name'].str.contains(library))]['No. of Methods Called By Tests'].values[0]
	noMethodsInLib = libsInfoDf.loc[(libsInfoDf['Library Name'].str.contains(library))]['No. of Public/Protected Methods'].values[0]
	return noMethodsCalledByTests/noMethodsInLib

def Q2(library, clients, pathToCsvs):
	callerCallee = pd.DataFrame()
	for client in clients:
		df = pd.read_csv(os.path.join(pathToCsvs,client+'.tsv'),sep='\t')
		df2 = df.loc[(df['Callee Library'].str.contains(library))& (df['Caller Library'] == client)]
		frames = [callerCallee, df2]
		callerCallee = pd.concat(frames)
	uniqueCalleeCount = len(callerCallee["Callee Method"].unique())
	libsInfoDf = pd.read_csv(os.path.join(pathToCsvs,'libs-info.tsv'),sep='\t')
	libraryInfoCount = libsInfoDf.loc[(libsInfoDf['Library Name'].str.contains(library))]['No. of Public/Protected Methods'].values[0]
	
	return uniqueCalleeCount/libraryInfoCount
	
	
def Q3(client, library, pathToCsvs):
    df = pd.read_csv(os.path.join(pathToCsvs,client+'.tsv'),sep='\t')
    callerCallee = df.loc[(df['Callee Library'].str.contains(library))& (df['Caller Library'] == client)]
    uniqueCallerCount = len(callerCallee["Callee Method"].unique())
    return uniqueCallerCount


def Q4(clients, library, pathToCsvs, topCount):
    #get unique count for each client
    if len(clients) < topCount :
        return 0
    calleeCountMap = {}
    for client in clients:
        calleeCountMap[client] = Q3(client, library, pathToCsvs)
    #sort based on the count values
    sortedCountMap = sorted(calleeCountMap.items(), key=lambda x: x[1], reverse=True)
    sortedCountMapDict = dict(sortedCountMap)
    #get the unique methods called by the not top 5 clients
    topNMethods = set()
    for client in dict(itertools.islice(sortedCountMapDict.items(), topCount)):
        df = pd.read_csv(os.path.join(pathToCsvs,client+'.tsv'),sep='\t')
        callerCalleeMethods = df.loc[(df['Callee Library'].str.contains(library))& (df['Caller Library'] == client)]
        topNMethods.update(callerCalleeMethods["Callee Method"].unique())
    #get the unique methods being used by the top 5 clients
    topNotNMethods = set()
    for client in dict(itertools.islice(sortedCountMapDict.items(), topCount, len(sortedCountMapDict.items()))):
        df = pd.read_csv(os.path.join(pathToCsvs,client+'.tsv'),sep='\t')
        callerCalleeMethods = df.loc[(df['Callee Library'].str.contains(library))& (df['Caller Library'] == client)]
        topNotNMethods.update(callerCalleeMethods["Callee Method"].unique())

    #intersect these two sets, and subtract from unique of non top 5
    intersectionSet = topNMethods.intersection(topNotNMethods)
    countNotBeingCalledByTop5 = len(topNotNMethods) - len(intersectionSet)
    #divide it by the number of public methods
    libsInfoDf = pd.read_csv(os.path.join(pathToCsvs,'libs-info.tsv'),sep='\t')
    libraryInfoCount = libsInfoDf.loc[(libsInfoDf['Library Name'].str.contains(library))]['No. of Public/Protected Methods'].values[0]
    
    return countNotBeingCalledByTop5/libraryInfoCount



toProcess = {'jsoup-':['JsoupXpath-2.4.3', 'errai-4.12.0']}
pathToCsvs = os.path.join(os.path.dirname(os.getcwd()),'calls-data')

# API Proportion
for library in toProcess:
	for client in toProcess[library]:
		print(apiProportion(client,library,pathToCsvs))
		
# Jaccard Similarity
for library in toProcess:
	for client1 in toProcess[library]:
		for client2 in toProcess[library]:
			print(jaccardSimilarity(client1,client2,library,pathToCsvs))
		
# Q1
for library in toProcess:		
	print(Q1(library,pathToCsvs))
	
# Q2
for library in toProcess:		
	print(Q2(library,toProcess[library],pathToCsvs))
