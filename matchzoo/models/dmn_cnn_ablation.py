# -*- coding=utf-8 -*-
'''
Implementation of DMN/DMN-PRF for conversational response ranking in information-seeking conversation
Model ablation analysis for DMN-PRF model

Reference:
Liu Yang, Minghui Qiu, Chen Qu, Jiafeng Guo, Yongfeng Zhang, W. Bruce Croft, Jun Huang, Haiqing Chen.
Response Ranking with Deep Matching Networks and External Knowledge in Information-seeking Conversation Systems. SIGIR 2018.

@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''

import keras
import keras.backend as K
from keras.models import Sequential, Model
from keras.layers import *
from keras.layers import Reshape, Embedding,Merge, Dot
from keras.optimizers import Adam
from model import BasicModel

import sys
sys.path.append('../matchzoo/layers/')
sys.path.append('../matchzoo/utils/')
from Match import *
from MatchBilinear import *
from utility import *

class DMN_CNN_ABLATION(BasicModel):
    def __init__(self, config):
        super(DMN_CNN_ABLATION, self).__init__(config)
        self.__name = 'DMN_CNN_ABLATION'
        self.check_list = [ 'text1_maxlen', 'text2_maxlen', 'text1_max_utt_num',
                   'embed', 'embed_size', 'train_embed',  'vocab_size',
                   'hidden_size', 'dropout_rate']
        self.embed_trainable = config['train_embed']
        self.setup(config)
        self.i = 0
        if not self.check():
            raise TypeError('[DMN_CNN_ABLATION] parameter check wrong')
        print '[DMN_CNN_ABLATION] init done'

    def setup(self, config):
        if not isinstance(config, dict):
            raise TypeError('parameter config should be dict:', config)

        self.set_default('hidden_size', 32)
        self.set_default('dropout_rate', 0)
        self.config.update(config)

    def build(self):
        def slice_reshape(x):
            print 'self.i', self.i, self.config['text1_maxlen']
            x1 = K.tf.slice(x, [0, self.i, 0], [-1, 1, self.config['text1_maxlen']])
            x2 = K.tf.reshape(tensor=x1, shape=(-1, self.config['text1_maxlen']))
            return x2

        def concate(x):
            return K.tf.concat([xx for xx in x], axis=3)

        def stack(x):
            return K.tf.stack([xx for xx in x], axis=1)

        query = Input(name='query', shape=(self.config['text1_max_utt_num'],self.config['text1_maxlen'],)) # get the data by name
        show_layer_info('Input query', query)
        doc = Input(name='doc', shape=(self.config['text2_maxlen'],))
        show_layer_info('Input doc', doc)

        embedding = Embedding(self.config['vocab_size'], self.config['embed_size'], weights=[self.config['embed']], trainable = self.embed_trainable)
        d_embed = embedding(doc)
        show_layer_info('Doc Embedding', d_embed)
        accum_stack = []

        # parameters for model ablation
        cross_matrix = self.config['cross_matrix'] if 'cross_matrix' in self.config else None
        print 'passed cross_matrix for model ablation: ', cross_matrix
        inter_type = self.config['inter_type']  if 'inter_type' in self.config else None
        print 'passed inter_type model ablation: ', inter_type

        for i in range(self.config['text1_max_utt_num']):
            self.i = i
            query_cur_utt = Lambda(slice_reshape)(query)
            show_layer_info('query_cur_utt', query_cur_utt)
            q_embed = embedding(query_cur_utt)
            show_layer_info('Query Embedding', q_embed)
            q_rep = Bidirectional(
                GRU(self.config['hidden_size'], return_sequences=True, dropout=self.config['dropout_rate']))(q_embed)
            show_layer_info('Bidirectional-GRU', q_rep)
            d_rep = Bidirectional(
                GRU(self.config['hidden_size'], return_sequences=True, dropout=self.config['dropout_rate']))(d_embed)
            show_layer_info('Bidirectional-GRU', d_rep)

            cross1 = None
            cross2 = None
            if cross_matrix != None and inter_type != None:
                raise TypeError('[DMN_CNN_ABLATION] cross_matrix and inter_type can not be passed together!')
            elif cross_matrix != None and inter_type == None:
                if cross_matrix == 'm1':  # only m1
                    cross1 = Match(match_type='dot')([q_embed, d_embed])  # dot product of embeddings
                elif cross_matrix == 'm2':  # only m2
                    cross2 = Match(match_type='dot')([q_rep, d_rep])  # dot product of GRU output representations
                else:
                    raise TypeError('[DMN_CNN_ABLATION] unrecognized cross_matrix!')
            elif cross_matrix == None and inter_type != None:
                if inter_type == 'dot':
                    cross1 = Match(match_type='dot')([q_embed, d_embed])  # dot product of embeddings
                    cross2 = Match(match_type='dot')([q_rep, d_rep])  # dot product of GRU output representations
                elif inter_type == 'cosine':
                    cross1 = Match(normalize=True, match_type='dot')([q_embed, d_embed])  # cosine product of embeddings
                    cross2 = Match(normalize=True, match_type='dot')([q_rep, d_rep])  # cosine product of GRU output representations
                elif inter_type == 'bilinear':
                    cross1 = MatchBilinear()([q_embed, d_embed])  # bilinear product of embeddings
                    cross2 = MatchBilinear()([q_rep, d_rep])  # bilinear product of GRU output representations
                else:
                    raise TypeError('[DMN_CNN_ABLATION] unrecognized inter_type!')
            else: # use the default setting if did not pass both
                cross1 = Match(match_type='dot')([q_embed, d_embed]) # dot product of embeddings
                cross2 = Match(match_type='dot')([q_rep, d_rep]) # dot product of GRU output representations

            if cross1 != None:
                show_layer_info('Match-dot1', cross1)
            if cross2 != None:
                show_layer_info('Match-dot2', cross2)
            if cross1 != None and cross2 != None:
                cross = Lambda(concate)([cross1, cross2])
                z = Reshape((self.config['text1_maxlen'], self.config['text2_maxlen'], 2))(cross) # batch_size * t1_len * t2_len * 2 channels
            elif cross1 == None and cross2 != None:
                z = cross2
            elif cross1 != None and cross2 == None:
                z = cross1
            else:
                raise TypeError('[DMN_CNN_ABLATION] both cross1 and cross2 are None!')
            show_layer_info('Reshape', z)

            for j in range(self.config['num_conv2d_layers']):
                z = Conv2D(filters=self.config['2d_kernel_counts'][j], kernel_size=self.config['2d_kernel_sizes'][j],
                           padding='valid', activation='relu')(z)
                show_layer_info('Conv2D', z)
                z = MaxPooling2D(pool_size=(self.config['2d_mpool_sizes'][j][0], self.config['2d_mpool_sizes'][j][1]))(z)
                show_layer_info('MaxPooling2D', z)

            z = Flatten()(z)
            show_layer_info('Flatten-z', z)
            z = Dense(50, activation="tanh")(z) # MLP  50 is the setting in Wu et al in ACL'17
            show_layer_info('Dense-z', z)
            accum_stack.append(z)

        accum_stack = Lambda(stack)(accum_stack) # batch_size * max_turn_num * 50
        show_layer_info('accum_stack', accum_stack)
        # GRU for Matching Accumulation
        accum_stack_gru_hidden = Bidirectional(
                GRU(self.config['hidden_size'], return_sequences=True, dropout=self.config['dropout_rate']))(accum_stack)
        show_layer_info('accum_stack_gru_hidden', accum_stack_gru_hidden)
        accum_stack_gru_hidden_flat = Reshape((-1,))(accum_stack_gru_hidden)
        show_layer_info('accum_stack_gru_hidden_flat', accum_stack_gru_hidden_flat)
        accum_stack_gru_hidden_flat_drop = Dropout(rate=self.config['dropout_rate'])(accum_stack_gru_hidden_flat)
        show_layer_info('Dropout', accum_stack_gru_hidden_flat_drop)

        # MLP
        if self.config['target_mode'] == 'classification':
            out_ = Dense(2, activation='softmax')(accum_stack_gru_hidden_flat_drop)
        elif self.config['target_mode'] in ['regression', 'ranking']:
            out_ = Dense(1)(accum_stack_gru_hidden_flat_drop)
        show_layer_info('Dense', out_)
        #model = Model(inputs=[query, doc, dpool_index], outputs=out_)
        model = Model(inputs=[query, doc], outputs=out_)
        return model
