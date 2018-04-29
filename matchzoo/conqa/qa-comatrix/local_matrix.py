
# coding: utf-8

from sklearn.feature_extraction.text import CountVectorizer
import numpy as np
import pandas as pd
import itertools
from copy import copy
import pickle, sys, re, json

# input param for this script
run_id = int(sys.argv[1]) # the batch id for the input files. we used distributed servers, the input files are segmented into multiple batches
dataset = sys.argv[2] # msdialog or udc
unique = sys.argv[3] # unique or nounique
qa_pairs_num = sys.argv[4] # how many QA pairs to use

filename = '/YOUR_PATH/local_matrix/{}/input/ids/{}.txt'.format(dataset, run_id) # sample ids for this batch
qa_pairs_dict_dir = '/YOUR_PATH/local_matrix/{}/input/qa_pairs_{}/{}.pkl'.format(dataset, qa_pairs_num, run_id) # QA pairs for this batch
terms_dir = '/YOUR_PATH/local_matrix/{}/input/terms/{}.txt'.format(dataset, run_id) # we only compute PMI that is relevant of terms in the samples
output_json = '/YOUR_PATH/local_matrix/{}/output/inter_{}/{}.json'.format(dataset, unique, run_id) # output file

with open(qa_pairs_dict_dir, 'rb') as handle:
    qa_pairs_dict = pickle.load(handle)

terms_dict = {}
with open(terms_dir) as fin:
    for line in fin:
        if line != '\n':
            tokens = line.strip().split('\t')
            instance_id = tokens[0]
            try:
                context_terms = set(tokens[1].split())
            except:
                context_terms = set()
            try:
                resp_terms = set(tokens[2].split())
            except:
                resp_terms = set()
            terms_dict[instance_id] = {'context_terms': context_terms, 'resp_terms': resp_terms}

def clean_str(string):
    # for html in ask ubuntu forum dump
    string = re.sub(r'&\w+;', " ", string)
    string = re.sub(r'&#\w+;', " ", string)
    string = re.sub(r"</*\w+>", " ", string)
    string = re.sub(r'<a href=".*">', " ", string)   
    string = re.sub(r'<\w+', " ", string)
    string = re.sub(r'\w+>', " ", string)
    string = re.sub(r'\w+=', " ", string)   
        
    string = re.sub(r"[^A-Za-z0-9(),!?\'\`]", " ", string)
    string = re.sub(r"\'s", " \'s", string)
    string = re.sub(r"\'ve", " \'ve", string)
    string = re.sub(r"n\'t", " n\'t", string)
    string = re.sub(r"\'re", " \'re", string)
    string = re.sub(r"\'d", " \'d", string)
    string = re.sub(r"\'ll", " \'ll", string)
    string = re.sub(r",", " , ", string)
    string = re.sub(r"!", " ! ", string)
    string = re.sub(r"\(", " \( ", string)
    string = re.sub(r"\)", " \) ", string)
    string = re.sub(r"\?", " \? ", string)
    string = re.sub(r"\s{2,}", " ", string)
    return string.strip().lower()

def tokenizer(doc):
    token_pattern = re.compile(r"(?u)\b\w\w+\b")
    return token_pattern.findall(doc)

    
def generate_vocab(qa_pairs, instance_id):
    questions = [qa_pair['question'] for qa_pair in qa_pairs]
    answers = [qa_pair['answer'] for qa_pair in qa_pairs]
    
    questions_cleaned = list(map(clean_str, list(questions)))
    answers_cleaned = list(map(clean_str, answers))
    
    count_vect_q = CountVectorizer(stop_words='english')
    counts_q = count_vect_q.fit_transform(questions_cleaned)    
    
    count_vect_a = CountVectorizer(stop_words='english')
    counts_a = count_vect_a.fit_transform(answers_cleaned)    
   
    q_vocab = set(count_vect_q.get_feature_names()) & terms_dict[instance_id]['context_terms']
    a_vocab = set(count_vect_a.get_feature_names()) & terms_dict[instance_id]['resp_terms']
    
    q_vocab_dict = {}
    a_vocab_dict = {}
    
    for term in q_vocab:
        q_vocab_dict[term] = 1
    for term in a_vocab:
        a_vocab_dict[term] = 1
    
    return q_vocab_dict, a_vocab_dict


def update_matrix(question, answer, matrix, q_vocab, a_vocab):

    q_tokens = tokenizer(clean_str(question))
    a_tokens = tokenizer(clean_str(answer))
    
    if unique == 'unique':
        q_tokens = set(q_tokens)
        a_tokens = set(a_tokens)
    
    q_tokens_filtered = list(filter(lambda x: x in q_vocab, list(q_tokens)))
    a_tokens_filtered = list(filter(lambda x: x in a_vocab, list(a_tokens)))
    
    combos = list(itertools.product(q_tokens_filtered, a_tokens_filtered))
    for q_token, a_token in combos:
        try:
            value = matrix.loc[q_token][a_token]
            matrix.set_value(q_token, a_token, value + 1)
        except:
            print('count matrix exception', q_token, a_token)


def generate_count_matrix(qa_pairs, q_vocab, a_vocab):
    terms_q = list(q_vocab.keys())
    terms_a = list(a_vocab.keys())
    co_occur_count_matrix = pd.DataFrame(columns=terms_a, index=terms_q)
    co_occur_count_matrix = co_occur_count_matrix.fillna(0)
    
    for qa_pair in qa_pairs:
        question = qa_pair['question']
        answer = qa_pair['answer']
        update_matrix(question, answer, co_occur_count_matrix, q_vocab, a_vocab)
        
    return co_occur_count_matrix


def generate_pmi_matrix(count_matrix, q_vocab, a_vocab):
    count_matrix = count_matrix.astype('float32')
    pmi_matrix = pd.DataFrame(columns=count_matrix.columns, index=count_matrix.index)
    q_count_sum = count_matrix.sum(axis=1)
    a_count_sum = count_matrix.sum(axis=0)
    total_count = count_matrix.values.sum()
    
    for q_token, row in count_matrix.iterrows():
        for a_token, value in row.iteritems():
            q_token_count = q_count_sum[q_token]
            a_token_count = a_count_sum[a_token]
            pmi = np.log(value * total_count / (q_token_count * a_token_count))
            ppmi = max(0, pmi)
            pmi_matrix.set_value(q_token, a_token, ppmi)
    return pmi_matrix


matrices = {}
computed = {}
instance_count = 0
with open(filename.format(run_id), encoding='utf-8') as fin:
    curr_instance_id = None
    for line in fin:
        if line == '\n':
            continue
        tokens = line.strip().split('\t')
        instance_id = tokens[0]
        if instance_id == curr_instance_id:
            pass
        else:
            if curr_instance_id:
                instance_count += 1
                print(instance_count)
                
                qa_pairs = qa_pairs_dict[curr_instance_id]
                q_vocab, a_vocab = generate_vocab(qa_pairs, curr_instance_id)
                count_matrix = generate_count_matrix(qa_pairs, q_vocab, a_vocab)
                pmi_matrix = generate_pmi_matrix(count_matrix, q_vocab, a_vocab)
                matrices[curr_instance_id] = pmi_matrix
            
            curr_instance_id = instance_id
    
    # for the last instance:
    if curr_instance_id:
        qa_pairs = qa_pairs_dict[curr_instance_id]
        q_vocab, a_vocab = generate_vocab(qa_pairs, curr_instance_id)
        count_matrix = generate_count_matrix(qa_pairs, q_vocab, a_vocab)
        pmi_matrix = generate_pmi_matrix(count_matrix, q_vocab, a_vocab)
        matrices[curr_instance_id] = pmi_matrix


instances_dict = {}
for instance_id, matrix in matrices.items():
    rcv_dict = {}
    r_vocab = {term: i for i, term in enumerate(list(matrix.index))}
    c_vocab = {term: i for i, term in enumerate(list(matrix.columns))}
    for q_term, row in matrix.iterrows():
        for a_term, value in row.iteritems():
            if value != 0:
                rcv_dict['{}_{}'.format(r_vocab[q_term], c_vocab[a_term])] = float("{0:.4f}".format(value))
    instances_dict[instance_id] = {'r_vocab': r_vocab, 'c_vocab': c_vocab, 'rcv_dict': rcv_dict}

with open(output_json, 'w') as handle:
    json.dump(instances_dict, handle)
