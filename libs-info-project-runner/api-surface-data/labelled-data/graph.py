import pandas as pd
import networkx as nx
import matplotlib.pyplot as plt

 
df = pd.read_csv('org.apache.rocketmq:rocketmq-acl:4.9.1-invocations.tsv', sep='\t')
G = nx.from_pandas_edgelist(df, source='Caller Method', target='Declared Callee Method', edge_attr='Label', create_using=nx.DiGraph())
nx.draw(G)
plt.savefig("filename.png")

