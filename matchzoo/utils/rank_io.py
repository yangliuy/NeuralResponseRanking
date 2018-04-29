#! encoding: utf-8
#! author: pangliang

import json
import numpy as np
import base64
from tqdm import tqdm

# Read Word Dict and Inverse Word Dict
def read_word_dict(filename):
    word_dict = {}
    iword_dict = {}
    for line in open(filename):
        line = line.strip().split()
        word_dict[int(line[1])] = line[0]
        iword_dict[line[0]] = int(line[1])
    print '[%s]\n\tWord dict size: %d' % (filename, len(word_dict))
    return word_dict, iword_dict

# Read Embedding File
def read_embedding(filename):
    embed = {}
    for line in open(filename):
        line = line.strip().split()
        embed[int(line[0])] = map(float, line[1:])
    print '[%s]\n\tEmbedding size: %d' % (filename, len(embed))
    return embed

# Read old version data
def read_data_old_version(filename):
    data = []
    for idx, line in enumerate(open(filename)):
        line = line.strip().split()
        len1 = int(line[1])
        len2 = int(line[2])
        data.append([map(int, line[3:3+len1]), map(int, line[3+len1:])])
        assert len2 == len(data[idx][1])
    print '[%s]\n\tInstance size: %d' % (filename, len(data))
    return data

# Read Relation Data
def read_relation(filename, verbose=True):
    data = []
    for line in open(filename):
        line = line.strip().split()
        data.append( (int(line[0]), line[1], line[2]) )
    if verbose:
        print '[%s]\n\tInstance size: %s' % (filename, len(data))
    return data

# Read varied-length features without id
def read_features_without_id(filename, verbose=True):
    features = []
    for line in open(filename):
        line = line.strip().split()
        features.append(map(float, line))
    if verbose:
        print '[%s]\n\tFeature size: %s' % (filename, len(features))
    return features

# Read varied-length features with id
def read_features_with_id(filename, verbose=True):
    features = {}
    for line in open(filename):
        line = line.strip().split()
        features[line[0]] = map(float, line)
    if verbose:
        print '[%s]\n\tFeature size: %s' % (filename, len(features))
    return features

# Read Data Dict
def read_data(filename, word_dict = None):
    data = {}
    for line in open(filename):
        line = line.strip().split()
        tid = line[0]
        if word_dict == None:
            data[tid] = map(int, line[2:])
        else:
            data[tid] = []
            for w in line[2:]:
                if w not in word_dict:
                    word_dict[w] = len(word_dict)
                data[tid].append(word_dict[w])
    print '[%s]\n\tData size: %s' % (filename, len(data))
    return data, word_dict

# Read Data Dict for 2D input
# A context could be made of multiple utterances
def read_data_2d(filename):
    data = {}
    for line in open(filename):
        line = line.strip().split('\t')
        tid = line[0]
        data[tid] = line[2:]
    print '[%s]\n\tData size: %s' % (filename, len(data))
    return data

# Read qa_comat.txt for QA co-occur matrix of DMN_KD model
def read_qa_comat(filename):
    qa_comat_dict = {}
    print 'read qa_comat ...'
    with open(filename) as fin:
        for l in tqdm(fin):
            to = l.strip().split('\t')
            #print 'test len(to): ', len(to)
            m = np.loads(base64.b32decode(to[1]))
            # print 'test m: ', m
            # to[0] key which is qid_uid_rid; to[1] is the corresponding
            # 3-row numpy array to denote the qa co-occur matrix
            m0 = np.asarray(m[0], dtype=np.int16)
            m1 = np.asarray(m[1], dtype=np.int16)
            qa_comat_dict[to[0]] = [m0,m1,m[2]]
    return qa_comat_dict

# Convert Embedding Dict 2 numpy array
def convert_embed_2_numpy(embed_dict, max_size=0, embed=None):
    feat_size = len(embed_dict[embed_dict.keys()[0]])
    if embed is None:
        embed = np.zeros( (max_size, feat_size), dtype = np.float32 )
    for k in embed_dict:
        embed[k] = np.array(embed_dict[k])
    print 'Generate numpy embed:', embed.shape
    return embed

